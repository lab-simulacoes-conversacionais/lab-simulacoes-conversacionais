package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.LimiteDoTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntasDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RoteiroDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TesteNaoPermitido;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.CenarioDaConversa;
import br.ufg.inf.labsimulacoes.ia.MensagemDaConversa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Modo de teste do cenário (RF-09): antes de aprovar, o curador conversa com o personagem da versão em
 * curadoria, com o mesmo adaptador e o mesmo montador de prompt da sala. O autor nunca testa a própria
 * versão (RN-05, RN-36).
 *
 * <p>O teste não é sessão de turma, por construção: não cria sessão nem mensagem, não usa o estado da
 * sessão no Redis e não publica evento nenhum. Grava só em pergunta_de_teste, e o consumo fica em
 * resposta_ia_meta, apontado pela pergunta (RNF-13). Por isso não entra nas métricas nem na fila do
 * curador.
 *
 * <p>O filtro é o da sala (RN-20), mas síncrono: modera a pergunta, gera a resposta inteira e modera a
 * resposta de uma vez, antes de devolver. Nenhuma chamada ao provedor fica dentro de transação; a
 * pergunta é gravada no fim, numa transação curta com o cenário travado, que confere de novo se a versão
 * continua em curadoria. Assim, uma pergunta respondida durante a devolução não vai para a rodada nova.
 *
 * <p>A contagem do RN-06 para o checklist (#27) é {@link PerguntasDeTeste#contar}.
 */
@Service
@PreAuthorize("hasRole('CURADOR')")
public class TesteDoCenario {

    /** O mesmo tamanho de histórico que a sala envia ao modelo. */
    static final int MENSAGENS_NO_HISTORICO = 20;

    private final Cenarios cenarios;
    private final PerguntasDeTeste perguntas;
    private final LimiteDoTeste limite;
    private final AdaptadorDeIa adaptador;
    private final UsuarioLogado usuarioLogado;
    private final TransactionTemplate transacao;

    TesteDoCenario(
            Cenarios cenarios,
            PerguntasDeTeste perguntas,
            LimiteDoTeste limite,
            AdaptadorDeIa adaptador,
            UsuarioLogado usuarioLogado,
            TransactionTemplate transacao) {
        this.cenarios = cenarios;
        this.perguntas = perguntas;
        this.limite = limite;
        this.adaptador = adaptador;
        this.usuarioLogado = usuarioLogado;
        this.transacao = transacao;
    }

    /**
     * O curador faz uma pergunta ao personagem e recebe a resposta inteira, já moderada. O pedido leva o
     * histórico da rodada do curador, sem as perguntas bloqueadas e as respostas bloqueadas, como na sala.
     *
     * <p>Se o filtro bloquear a pergunta, ela não chega ao modelo; se bloquear a resposta, ela não é
     * devolvida nem guardada. Nos dois casos a pergunta é gravada com a marca, e quem exibe mostra o
     * aviso neutro no lugar da resposta. Nada vai para a fila do curador.
     *
     * @return a pergunta gravada, com a resposta só no desfecho
     *     {@link PerguntaDeTeste.Desfecho#EXIBIDA}
     * @throws IllegalArgumentException se a pergunta estiver vazia
     * @throws CenarioNaoEncontrado se o cenário não existir
     * @throws TesteNaoPermitido se a versão não estiver em curadoria ou se o curador for o autor; também
     *     quando a versão sai da curadoria durante a resposta, e então nada é gravado
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.LimiteDoTesteAtingido antes de chamar o provedor,
     *     se o curador já fez todas as perguntas da rodada (RNF-13)
     * @throws br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa se o provedor ou a moderação falharem; nada
     *     é gravado em pergunta_de_teste, e a pergunta pode ser feita de novo
     */
    public PerguntaDeTeste perguntar(long cenarioId, String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Digite a pergunta de teste");
        }
        String pergunta = texto.strip();
        long curador = usuarioLogado.id();
        Cenario cenario = cenarios.buscar(cenarioId).orElseThrow(() -> new CenarioNaoEncontrado(cenarioId));
        long versaoId = PerguntaDeTeste.versaoTestavel(cenario, curador);
        limite.exigirDentro(perguntas.contar(versaoId, curador));

        ConteudoDoCenario testado = cenario.versao().conteudo();

        if (adaptador.moderar(pergunta).sinalizado()) {
            return gravar(cenarioId, testado, PerguntaDeTeste.comPerguntaBloqueada(versaoId, curador, pergunta));
        }

        PedidoDeFala pedido = new PedidoDeFala(paraCenario(testado), historico(versaoId, curador), pergunta);
        RespostaDoPersonagem resposta = adaptador.responder(pedido, trecho -> {});
        PerguntaDeTeste respondida = adaptador.moderar(resposta.texto()).sinalizado()
                ? PerguntaDeTeste.comRespostaBloqueada(versaoId, curador, pergunta, resposta.metadadosId())
                : PerguntaDeTeste.exibida(versaoId, curador, pergunta, resposta.texto(), resposta.metadadosId());
        return gravar(cenarioId, testado, respondida);
    }

    /**
     * Com o cenário travado, a devolução não fecha a rodada no meio da gravação, e uma pergunta respondida
     * sobre uma versão que saiu da curadoria ou mudou é recusada em vez de entrar na rodada seguinte.
     */
    private PerguntaDeTeste gravar(long cenarioId, ConteudoDoCenario testado, PerguntaDeTeste pergunta) {
        return transacao.execute(status -> {
            Cenario cenario = cenarios.buscarParaAlterar(cenarioId)
                    .orElseThrow(() -> new CenarioNaoEncontrado(cenarioId));
            PerguntaDeTeste.exigirAindaTestavel(cenario, pergunta.curadorId(), pergunta.versaoId(), testado);
            return perguntas.gravar(pergunta);
        });
    }

    /** As últimas falas exibidas na rodada; as bloqueadas ficam de fora (RN-20). */
    private List<MensagemDaConversa> historico(long versaoId, long curador) {
        List<MensagemDaConversa> falas = new ArrayList<>();
        for (PerguntaDeTeste anterior : perguntas.daRodada(versaoId, curador)) {
            if (anterior.desfecho() == PerguntaDeTeste.Desfecho.EXIBIDA) {
                falas.add(new MensagemDaConversa(MensagemDaConversa.Autor.TURMA, anterior.pergunta()));
                falas.add(new MensagemDaConversa(MensagemDaConversa.Autor.PERSONAGEM, anterior.resposta()));
            }
        }
        return List.copyOf(falas.subList(Math.max(0, falas.size() - MENSAGENS_NO_HISTORICO), falas.size()));
    }

    /** A versão em curadoria está completa (RN-02, RN-03), então tem os campos que o template usa. */
    private static CenarioDaConversa paraCenario(ConteudoDoCenario conteudo) {
        RoteiroDoCenario roteiro = conteudo.roteiro();
        VinculoCurricular curriculo = conteudo.curriculo();
        return new CenarioDaConversa(
                conteudo.titulo(),
                roteiro.personagem(),
                roteiro.papelDaIa(),
                roteiro.contexto(),
                roteiro.objetivos(),
                roteiro.limitesDeAssunto(),
                roteiro.fontes(),
                curriculo.componente(),
                curriculo.ano(),
                curriculo.habilidades(),
                conteudo.faixaEtaria());
    }
}
