package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.LimiteDoTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntasDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RodadaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RoteiroDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TesteNaoPermitido;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.CenarioDaConversa;
import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import br.ufg.inf.labsimulacoes.ia.MensagemDaConversa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Cada pergunta passa por três passos, e nenhuma chamada à IA fica dentro de transação:
 *
 * <ol>
 *   <li>Reserva, numa transação curta com o cenário travado: confere se a versão está em curadoria, se
 *       o curador não é o autor e se ainda cabe uma pergunta no teto, e grava a pergunta como pendente na
 *       rodada em aberto. Com a trava, dois pedidos ao mesmo tempo nunca passam do teto.
 *   <li>Fora de transação, o filtro da sala (RN-20), síncrono: modera a pergunta, gera a resposta inteira
 *       e modera a resposta de uma vez.
 *   <li>Resultado, noutra transação curta, na mesma pergunta reservada: concluída, pergunta bloqueada,
 *       resposta bloqueada ou falha. A resposta já gerada fica sempre ligada à pergunta, também na falha.
 * </ol>
 *
 * <p>São duas contagens: o teto (RNF-13) conta todas as perguntas da rodada, e o RN-06
 * ({@link PerguntasDeTeste#contar}, para o checklist da #27) conta só as que testaram o personagem.
 */
@Service
@PreAuthorize("hasRole('CURADOR')")
public class TesteDoCenario {

    /** O mesmo tamanho de histórico que a sala envia ao modelo. */
    static final int MENSAGENS_NO_HISTORICO = 20;

    private static final Logger LOG = LoggerFactory.getLogger(TesteDoCenario.class);

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

    /** O que a reserva leva para a chamada à IA, lido com o cenário travado. */
    private record Reserva(PerguntaDeTeste pergunta, ConteudoDoCenario conteudo, List<MensagemDaConversa> historico) {}

    /**
     * O curador faz uma pergunta ao personagem e recebe a resposta inteira, já moderada. O pedido leva o
     * histórico da rodada do curador, só com as perguntas concluídas, como na sala.
     *
     * <p>Se o filtro bloquear a pergunta, ela não chega ao modelo; se bloquear a resposta, ela não é
     * devolvida nem guardada. Nos dois casos a pergunta fica com a marca, e quem exibe mostra o aviso
     * neutro no lugar da resposta. Nada vai para a fila do curador.
     *
     * @return a pergunta com o resultado; a resposta só existe na situação
     *     {@link PerguntaDeTeste.Situacao#CONCLUIDA}
     * @throws IllegalArgumentException se a pergunta estiver vazia ou passar de
     *     {@value PerguntaDeTeste#TAMANHO_MAXIMO_DA_PERGUNTA} caracteres
     * @throws CenarioNaoEncontrado se o cenário não existir
     * @throws TesteNaoPermitido se a versão não estiver em curadoria ou se o curador for o autor; também
     *     quando uma devolução fecha a rodada durante a resposta, e então a pergunta fica guardada na rodada
     *     fechada
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.LimiteDoTesteAtingido antes de chamar o provedor,
     *     se a rodada do curador já tem todas as perguntas do teto (RNF-13)
     * @throws br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa se o provedor ou a moderação falharem; a
     *     pergunta fica com a situação de falha, ocupando a vaga, e pode ser feita de novo
     */
    public PerguntaDeTeste perguntar(long cenarioId, String texto) {
        String pergunta = PerguntaDeTeste.textoDaPergunta(texto);
        long curador = usuarioLogado.id();
        Reserva reserva = reservar(cenarioId, curador, pergunta);

        PerguntaDeTeste comResultado = consultarAIa(reserva);

        PerguntaDeTeste gravada = transacao.execute(status -> perguntas.concluir(comResultado));
        if (gravada.rodadaFechada()) {
            throw TesteNaoPermitido.rodadaFechada(gravada.versaoId());
        }
        return gravada;
    }

    /**
     * A rodada em aberto do curador logado na versão em curadoria do cenário, para a tela do modo de
     * teste: a conversa e as contagens do RN-06 e do teto. Só lê; nada é travado nem gravado.
     *
     * @throws CenarioNaoEncontrado se o cenário não existir
     * @throws TesteNaoPermitido se a versão não estiver em curadoria ou se o curador for o autor
     */
    public RodadaDeTeste rodada(long cenarioId) {
        Cenario cenario = cenarios.buscar(cenarioId).orElseThrow(() -> new CenarioNaoEncontrado(cenarioId));
        long curador = usuarioLogado.id();
        long versaoId = PerguntaDeTeste.versaoTestavel(cenario, curador);
        return new RodadaDeTeste(cenario.versao().conteudo().roteiro().personagem(),
                perguntas.daRodada(versaoId, curador), limite.perguntasPorVersao());
    }

    /** Passo 1: com o cenário travado, os pedidos do mesmo cenário reservam um de cada vez. */
    private Reserva reservar(long cenarioId, long curador, String pergunta) {
        return transacao.execute(status -> {
            Cenario cenario = cenarios.buscarParaAlterar(cenarioId)
                    .orElseThrow(() -> new CenarioNaoEncontrado(cenarioId));
            long versaoId = PerguntaDeTeste.versaoTestavel(cenario, curador);
            List<PerguntaDeTeste> rodada = perguntas.daRodada(versaoId, curador);
            limite.exigirDentro(perguntas.ocupadas(versaoId, curador));
            PerguntaDeTeste reservada = perguntas.reservar(PerguntaDeTeste.reservada(versaoId, curador, pergunta));
            return new Reserva(reservada, cenario.versao().conteudo(), historico(rodada));
        });
    }

    /**
     * Passo 2, fora de transação. Uma falha depois de a IA responder fica com os metadados da resposta,
     * que já foi paga (RNF-13).
     */
    private PerguntaDeTeste consultarAIa(Reserva reserva) {
        PerguntaDeTeste pendente = reserva.pergunta();
        Long metadados = null;
        try {
            if (adaptador.moderar(pendente.pergunta()).sinalizado()) {
                return pendente.comPerguntaBloqueada();
            }
            // RNF-13: o consumo do teste fica ligado à versão testada, separado do da sala.
            RespostaDoPersonagem resposta = adaptador.responder(
                    new PedidoDeFala(paraCenario(reserva.conteudo()), reserva.historico(), pendente.pergunta(),
                            FinalidadeDoConsumo.TESTE_DO_CURADOR, pendente.versaoId()),
                    trecho -> {});
            metadados = resposta.metadadosId();
            return adaptador.moderar(resposta.texto()).sinalizado()
                    ? pendente.comRespostaBloqueada(resposta.metadadosId())
                    : pendente.concluida(resposta.texto(), resposta.metadadosId());
        } catch (RuntimeException falha) {
            registrarFalha(pendente.comFalha(metadados), falha);
            throw falha;
        }
    }

    /** Se nem a falha puder ser gravada, a pergunta fica pendente, ocupando só a sua vaga. */
    private void registrarFalha(PerguntaDeTeste comFalha, RuntimeException falha) {
        try {
            transacao.executeWithoutResult(status -> perguntas.concluir(comFalha));
        } catch (RuntimeException naGravacao) {
            LOG.warn("Não deu para gravar a falha da pergunta de teste {}; ela fica pendente", comFalha.id(),
                    naGravacao);
            falha.addSuppressed(naGravacao);
        }
    }

    /** As últimas falas exibidas na rodada; as bloqueadas, pendentes e com falha ficam de fora (RN-20). */
    private static List<MensagemDaConversa> historico(List<PerguntaDeTeste> rodada) {
        List<MensagemDaConversa> falas = new ArrayList<>();
        for (PerguntaDeTeste anterior : rodada) {
            if (anterior.situacao() == PerguntaDeTeste.Situacao.CONCLUIDA) {
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
