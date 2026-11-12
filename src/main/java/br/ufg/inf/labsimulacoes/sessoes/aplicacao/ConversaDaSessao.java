package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import br.ufg.inf.labsimulacoes.eventos.ConteudoBloqueado;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.CenarioDaConversa;
import br.ufg.inf.labsimulacoes.ia.MensagemDaConversa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MudancaNaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.PerguntaBloqueada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.RespostaEmAndamento;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O professor digita a pergunta da turma e o personagem responde em frases moderadas (RF-16, RF-22,
 * Stack, seção 5, fluxo de uma pergunta). Só o professor que iniciou a sessão conversa nela (RN-10,
 * RN-12).
 *
 * <p>São dois passos, para a tela receber a resposta por SSE: {@link #enviarPergunta} modera e grava a
 * pergunta e {@link #responderPendente} gera a resposta da pergunta que ficou pendente. Nenhuma chamada
 * ao provedor fica dentro de uma transação: a moderação da pergunta vem antes da transação que grava, e
 * a resposta é gravada no fim, numa transação curta.
 *
 * <p>Quando o filtro bloqueia a pergunta ou uma frase da resposta (RN-20), nada do conteúdo bloqueado é
 * exibido, a sessão pausa com o motivo {@link MotivoDaPausa#CONTEUDO_BLOQUEADO} e sai o evento
 * {@link ConteudoBloqueado}, que abre o caso na fila do curador. Quem retoma é o professor.
 */
@Service
@PreAuthorize("hasRole('PROFESSOR')")
public class ConversaDaSessao {

    private static final Logger LOG = LoggerFactory.getLogger(ConversaDaSessao.class);

    private final Sessoes sessoes;
    private final EstadosDasSessoes estados;
    private final LeituraDaSessao leitura;
    private final CenariosAprovados cenarios;
    private final AdaptadorDeIa adaptador;
    private final TransactionTemplate transacao;
    private final Relogio relogio;
    private final ApplicationEventPublisher eventos;

    /**
     * Sessões em que o personagem está respondendo agora, com a marca de quem responde, para uma
     * resposta só liberar a sua vez. Fica em memória porque a aplicação roda numa instância só (Stack,
     * seção 4); com mais de uma, precisaria ir para o Redis.
     */
    private final ConcurrentMap<Long, Object> respondendo = new ConcurrentHashMap<>();

    ConversaDaSessao(
            Sessoes sessoes,
            EstadosDasSessoes estados,
            LeituraDaSessao leitura,
            CenariosAprovados cenarios,
            AdaptadorDeIa adaptador,
            TransactionTemplate transacao,
            Relogio relogio,
            ApplicationEventPublisher eventos) {
        this.sessoes = sessoes;
        this.estados = estados;
        this.leitura = leitura;
        this.cenarios = cenarios;
        this.adaptador = adaptador;
        this.transacao = transacao;
        this.relogio = relogio;
        this.eventos = eventos;
    }

    /**
     * Modera a pergunta (RN-20) e a grava com a autoria genérica "Turma" (RN-11), atualizando o estado da
     * sessão no Redis.
     *
     * <p>A sessão é conferida antes da moderação, para não moderar uma pergunta que seria recusada, e de
     * novo depois, já com o lock, porque a moderação é uma chamada externa e o lock não fica preso
     * durante ela. O lock serializa dois envios simultâneos: o segundo já vê a primeira pergunta
     * pendente, e o limite vale mesmo assim (RN-18).
     *
     * <p>Uma pergunta bloqueada não vira mensagem nem chega ao modelo, e não conta no limite.
     *
     * @throws IllegalArgumentException se a pergunta estiver vazia
     * @throws PerguntaBloqueada se o filtro bloqueou a pergunta; a sessão já foi pausada
     * @throws br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa se a moderação falhar; nada é gravado
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.LimiteDePerguntasAtingido se a turma já fez todas
     *     as perguntas (RN-18)
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado se a sessão estiver pausada
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada se já estiver encerrada (RN-19)
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.PerguntaSemResposta se a pergunta anterior ainda
     *     espera a resposta, porque o personagem está respondendo ou o provedor falhou
     */
    public PerguntaRecebida enviarPergunta(long sessaoId, String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Digite a pergunta da turma");
        }
        String pergunta = texto.strip();
        leitura.doProfessorLogado(sessoes.buscar(sessaoId), sessaoId).aceitarPergunta(leitura.doBanco(sessaoId));
        ResultadoDaModeracao moderacao = adaptador.moderar(pergunta);
        Optional<PerguntaRecebida> recebida = transacao.execute(status -> gravarPergunta(sessaoId, pergunta, moderacao));
        if (recebida.isEmpty()) {
            throw new PerguntaBloqueada(sessaoId);
        }
        return recebida.get();
    }

    /** @return vazio se a pergunta foi bloqueada */
    private Optional<PerguntaRecebida> gravarPergunta(long sessaoId, String texto, ResultadoDaModeracao moderacao) {
        Sessao sessao = leitura.doProfessorLogado(sessoes.buscarParaAlterar(sessaoId), sessaoId);
        EstadoDaSessao antes = leitura.doBanco(sessaoId);
        sessao.aceitarPergunta(antes);
        Instant agora = relogio.agora();
        if (moderacao.sinalizado()) {
            EstadoDaSessao depois = bloquear(sessao, agora).map(pausa -> antes.aplicar(pausa.evento())).orElse(antes);
            estados.gravar(depois);
            eventos.publishEvent(new ConteudoBloqueado(
                    sessaoId, sessao.versaoId(), ConteudoBloqueado.Origem.PERGUNTA, texto, null,
                    moderacao.categorias(), null, agora));
            return Optional.empty();
        }
        var pergunta = new MensagemExibida(MensagemExibida.Autoria.TURMA, texto, agora);
        sessoes.registrarMensagem(sessaoId, pergunta, null);
        EstadoDaSessao depois = antes.registrarMensagem(pergunta);
        estados.gravar(depois);
        return Optional.of(new PerguntaRecebida(pergunta, sessao.perguntasRestantes(depois.perguntasDaTurma())));
    }

    /**
     * Gera a resposta do personagem à pergunta pendente e entrega à tela, na ordem, cada frase que a
     * moderação aprovou, enquanto o modelo gera. A resposta completa é gravada com os metadados (RNF-10).
     * O histórico enviado ao modelo começa no último reinício (RN-19), tem até
     * {@link EstadoDaSessao#MENSAGENS_GUARDADAS} mensagens e deixa de fora as perguntas cuja resposta
     * foi bloqueada (RN-20).
     *
     * <p>Se não houver pergunta pendente, porque a tela reconectou depois da resposta, não chama o
     * modelo e devolve vazio. Se o provedor ou a moderação falharem, a pergunta continua pendente e é
     * respondida quando o canal for aberto de novo (RNF-04); até lá, a turma não envia outra.
     *
     * <p>Se uma frase for bloqueada (RN-20), ela e as seguintes não chegam à tela. A sessão pausa e a
     * pergunta deixa de ficar pendente e o evento {@link ConteudoBloqueado} sai antes de a tela ser
     * avisada; depois disso, o restante da resposta é consumido sem exibir, para os metadados terem os
     * tokens consumidos (RNF-13). Esse consumo não prende a sessão: depois de retomar, a resposta à
     * pergunta seguinte já pode ser gerada. A resposta não é gravada como mensagem, e o evento sai de novo no fim,
     * com os metadados.
     *
     * @return a resposta exibida, ou vazio se não havia pergunta pendente ou se a resposta foi bloqueada
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado se a sessão estiver pausada
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada se já estiver encerrada (RN-19)
     * @throws RespostaEmAndamento se outra resposta estiver sendo gerada nesta sessão e ainda não tiver
     *     sido bloqueada
     * @throws br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa se o provedor ou a moderação falharem antes
     *     de um bloqueio
     */
    public Optional<MensagemExibida> responderPendente(long sessaoId, TelaDaResposta tela) {
        Sessao sessao = leitura.doProfessorLogado(sessoes.buscar(sessaoId), sessaoId);
        Object vez = new Object();
        if (respondendo.putIfAbsent(sessaoId, vez) != null) {
            throw new RespostaEmAndamento(sessaoId);
        }
        try {
            EstadoDaSessao estado = leitura.doBanco(sessaoId);
            Optional<MensagemExibida> pendente = estado.perguntaPendente();
            if (pendente.isEmpty()) {
                return Optional.empty();
            }
            sessao.aceitarResposta();
            MensagemExibida pergunta = pendente.get();
            AtomicReference<ConteudoBloqueado> bloqueado = new AtomicReference<>();
            var moderacao = new ModeracaoDaResposta(adaptador, tela::exibir, bloqueio -> {
                bloqueado.set(pausarPorBloqueioDaResposta(sessaoId, pergunta, bloqueio));
                // O restante só serve aos metadados: a sessão fica livre para a próxima resposta.
                respondendo.remove(sessaoId, vez);
                avisarBloqueio(tela, sessaoId);
            });

            RespostaDoPersonagem resposta;
            try {
                resposta = adaptador.responder(pedido(sessao, estado, pergunta), moderacao::receber);
                moderacao.terminar();
            } catch (RuntimeException e) {
                if (bloqueado.get() == null) {
                    throw e;
                }
                // A tela já foi avisada, a sessão está pausada e o caso, aberto: ele fica sem os metadados.
                LOG.warn("O provedor falhou depois do bloqueio na sessão {}", sessaoId, e);
                return Optional.empty();
            }

            if (bloqueado.get() != null) {
                ConteudoBloqueado comMetadados = bloqueado.get().comMetadados(resposta.metadadosId());
                transacao.executeWithoutResult(status -> eventos.publishEvent(comMetadados));
                return Optional.empty();
            }
            var exibida = new MensagemExibida(MensagemExibida.Autoria.IA, resposta.texto(), relogio.agora());
            transacao.executeWithoutResult(status -> gravarResposta(sessaoId, exibida, resposta.metadadosId()));
            return Optional.of(exibida);
        } finally {
            respondendo.remove(sessaoId, vez);
        }
    }

    /**
     * Pausa a sessão, tira a pergunta da espera e publica o {@link ConteudoBloqueado}, numa transação
     * própria, antes de a tela saber do bloqueio. Com o evento na mesma transação da pausa, o registro de
     * publicação garante o caso do curador mesmo que a aplicação pare antes do fim da resposta. Se a
     * sessão já tiver sido pausada ou encerrada durante a resposta, ela fica como está.
     *
     * @return o evento publicado, ainda sem os metadados da resposta
     */
    private ConteudoBloqueado pausarPorBloqueioDaResposta(
            long sessaoId, MensagemExibida pergunta, ModeracaoDaResposta.Bloqueio bloqueio) {
        return transacao.execute(status -> {
            Sessao sessao = sessoes.buscarParaAlterar(sessaoId).orElseThrow(() -> new SessaoNaoEncontrada(sessaoId));
            Instant agora = relogio.agora();
            sessoes.marcarRespostaBloqueada(sessaoId, pergunta);
            bloquear(sessao, agora);
            estados.gravar(leitura.doBanco(sessaoId));
            var evento = new ConteudoBloqueado(
                    sessaoId, sessao.versaoId(), ConteudoBloqueado.Origem.RESPOSTA, pergunta.texto(), bloqueio.frase(),
                    bloqueio.categorias(), null, agora);
            eventos.publishEvent(evento);
            return evento;
        });
    }

    /**
     * RN-20: pausa a sessão se ela ainda estiver em andamento ({@link Sessao#bloquearConteudo}).
     *
     * @return a pausa gravada, ou vazio se a sessão não estava em andamento
     */
    private Optional<MudancaNaSessao> bloquear(Sessao sessao, Instant em) {
        Optional<MudancaNaSessao> pausa = sessao.bloquearConteudo(em);
        pausa.ifPresent(mudanca -> {
            sessoes.salvar(mudanca.sessao());
            sessoes.registrar(sessao.id(), mudanca.evento());
        });
        return pausa;
    }

    /** A tela que fechou não impede o resto: o stream continua para os tokens serem gravados. */
    private static void avisarBloqueio(TelaDaResposta tela, long sessaoId) {
        try {
            tela.bloquear();
        } catch (RuntimeException e) {
            LOG.info("Não deu para avisar a tela da sessão {} sobre o bloqueio", sessaoId, e);
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
        List<MensagemDaConversa> historico = estado.historicoParaOModelo().stream()
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
