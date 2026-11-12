package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.CenarioDaConversa;
import br.ufg.inf.labsimulacoes.ia.MensagemDaConversa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida;
import br.ufg.inf.labsimulacoes.sessoes.dominio.RespostaEmAndamento;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O professor digita a pergunta da turma e o personagem responde em trechos (RF-16, Stack, seção 5,
 * fluxo de uma pergunta). Só o professor que iniciou a sessão conversa nela (RN-10, RN-12).
 *
 * <p>São dois passos, para a tela receber a resposta por SSE: {@link #enviarPergunta} grava a pergunta
 * e {@link #responderPendente} gera a resposta da pergunta que ficou pendente. O streaming não fica
 * dentro de uma transação, porque leva segundos; a resposta é gravada no fim, numa transação curta.
 *
 * <p>Ainda falta a moderação da pergunta e de cada frase da resposta antes da tela (RF-22, issue #18):
 * ela entra entre o adaptador e {@code aoReceberTrecho}.
 */
@Service
@PreAuthorize("hasRole('PROFESSOR')")
public class ConversaDaSessao {

    private final Sessoes sessoes;
    private final EstadosDasSessoes estados;
    private final LeituraDaSessao leitura;
    private final CenariosAprovados cenarios;
    private final AdaptadorDeIa adaptador;
    private final TransactionTemplate transacao;
    private final Relogio relogio;

    /**
     * Sessões em que o personagem está respondendo agora. Fica em memória porque a aplicação roda numa
     * instância só (Stack, seção 4); com mais de uma, precisaria ir para o Redis.
     */
    private final Set<Long> respondendo = ConcurrentHashMap.newKeySet();

    ConversaDaSessao(
            Sessoes sessoes,
            EstadosDasSessoes estados,
            LeituraDaSessao leitura,
            CenariosAprovados cenarios,
            AdaptadorDeIa adaptador,
            TransactionTemplate transacao,
            Relogio relogio) {
        this.sessoes = sessoes;
        this.estados = estados;
        this.leitura = leitura;
        this.cenarios = cenarios;
        this.adaptador = adaptador;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    /**
     * Grava a pergunta com a autoria genérica "Turma" (RN-11) e atualiza o estado da sessão no Redis. O
     * lock da sessão serializa dois envios simultâneos: o segundo já vê a primeira pergunta pendente, e
     * o limite vale mesmo assim (RN-18).
     *
     * @throws IllegalArgumentException se a pergunta estiver vazia
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.LimiteDePerguntasAtingido se a turma já fez todas
     *     as perguntas (RN-18)
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado se a sessão estiver pausada
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada se já estiver encerrada (RN-19)
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.PerguntaSemResposta se a pergunta anterior ainda
     *     espera a resposta, porque o personagem está respondendo ou o provedor falhou
     */
    @Transactional
    public PerguntaRecebida enviarPergunta(long sessaoId, String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Digite a pergunta da turma");
        }
        Sessao sessao = leitura.doProfessorLogado(sessoes.buscarParaAlterar(sessaoId), sessaoId);
        EstadoDaSessao antes = leitura.doBanco(sessaoId);
        sessao.aceitarPergunta(antes);
        var pergunta = new MensagemExibida(MensagemExibida.Autoria.TURMA, texto.strip(), relogio.agora());
        sessoes.registrarMensagem(sessaoId, pergunta, null);
        EstadoDaSessao depois = antes.registrarMensagem(pergunta);
        estados.gravar(depois);
        return new PerguntaRecebida(pergunta, sessao.perguntasRestantes(depois.perguntasDaTurma()));
    }

    /**
     * Gera a resposta do personagem à pergunta pendente, entregando os trechos a {@code aoReceberTrecho}
     * enquanto chegam, e grava a resposta completa com os metadados (RNF-10). O histórico enviado ao
     * modelo começa no último reinício (RN-19) e tem até {@link EstadoDaSessao#MENSAGENS_GUARDADAS}
     * mensagens.
     *
     * <p>Se não houver pergunta pendente, porque a tela reconectou depois da resposta, não chama o
     * modelo e devolve vazio. Se o provedor falhar, a pergunta continua pendente e é respondida quando o
     * canal for aberto de novo (RNF-04); até lá, a turma não envia outra.
     *
     * @return a resposta exibida, ou vazio se não havia pergunta pendente
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado se a sessão estiver pausada
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada se já estiver encerrada (RN-19)
     * @throws RespostaEmAndamento se outra resposta estiver sendo gerada nesta sessão
     * @throws br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa se o provedor falhar
     */
    public Optional<MensagemExibida> responderPendente(long sessaoId, Consumer<String> aoReceberTrecho) {
        Sessao sessao = leitura.doProfessorLogado(sessoes.buscar(sessaoId), sessaoId);
        if (!respondendo.add(sessaoId)) {
            throw new RespostaEmAndamento(sessaoId);
        }
        try {
            EstadoDaSessao estado = leitura.doBanco(sessaoId);
            Optional<MensagemExibida> pendente = estado.perguntaPendente();
            if (pendente.isEmpty()) {
                return Optional.empty();
            }
            sessao.aceitarResposta();
            RespostaDoPersonagem resposta = adaptador.responder(pedido(sessao, estado, pendente.get()), aoReceberTrecho);
            var exibida = new MensagemExibida(MensagemExibida.Autoria.IA, resposta.texto(), relogio.agora());
            transacao.executeWithoutResult(status -> gravarResposta(sessaoId, exibida, resposta.metadadosId()));
            return Optional.of(exibida);
        } finally {
            respondendo.remove(sessaoId);
        }
    }

    /**
     * A resposta já apareceu na tela, então é gravada mesmo que a sessão tenha sido pausada ou encerrada
     * durante o streaming: toda fala exibida em sala precisa do seu registro (RNF-10).
     */
    private void gravarResposta(long sessaoId, MensagemExibida resposta, long metadadosId) {
        sessoes.buscarParaAlterar(sessaoId);
        sessoes.registrarMensagem(sessaoId, resposta, metadadosId);
        estados.gravar(leitura.doBanco(sessaoId));
    }

    private PedidoDeFala pedido(Sessao sessao, EstadoDaSessao estado, MensagemExibida pendente) {
        VersaoAprovada versao = cenarios.versaoAprovada(sessao.versaoId())
                .orElseThrow(() -> new IllegalStateException(
                        "A versão " + sessao.versaoId() + " da sessão " + sessao.id() + " não está aprovada"));
        List<MensagemExibida> mensagens = estado.ultimasMensagens();
        List<MensagemDaConversa> historico = mensagens.subList(0, mensagens.size() - 1).stream()
                .map(ConversaDaSessao::paraConversa)
                .toList();
        return new PedidoDeFala(paraCenario(versao), historico, pendente.texto());
    }

    private static MensagemDaConversa paraConversa(MensagemExibida mensagem) {
        MensagemDaConversa.Autor autor = switch (mensagem.autoria()) {
            case TURMA -> MensagemDaConversa.Autor.TURMA;
            case IA -> MensagemDaConversa.Autor.PERSONAGEM;
        };
        return new MensagemDaConversa(autor, mensagem.texto());
    }

    private static CenarioDaConversa paraCenario(VersaoAprovada versao) {
        return new CenarioDaConversa(
                versao.titulo(),
                versao.personagem(),
                versao.papelDaIa(),
                versao.contexto(),
                versao.objetivos(),
                versao.limitesDeAssunto(),
                versao.fontes(),
                versao.componente(),
                versao.ano(),
                versao.habilidades(),
                versao.faixaEtaria());
    }
}
