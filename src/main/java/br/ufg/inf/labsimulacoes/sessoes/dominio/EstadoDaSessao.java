package br.ufg.inf.labsimulacoes.sessoes.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Estado "quente" da sessão, guardado no Redis para a tela de projeção ser reconstruída sem consultas
 * pesadas ao banco (RNF-04, Stack, seção 5). O PostgreSQL continua sendo a fonte da verdade: este
 * estado sempre pode ser refeito a partir dele com {@link #reconstruir(HistoricoDaSessao)}.
 *
 * <p>As mudanças passam pelos mesmos métodos na atualização a cada interação e na reconstrução, para
 * que os dois caminhos cheguem ao mesmo resultado.
 *
 * @param motivoDaPausa presente só enquanto a sessão está pausada
 * @param tempoEmPausa soma das pausas já retomadas; a pausa atual conta a partir de {@code pausadaDesde}
 * @param pausadaDesde início da pausa atual, ou {@code null}
 * @param reiniciadaEm último ponto de corte, ou {@code null} se a sessão nunca foi reiniciada
 * @param perguntasDaTurma todas as perguntas da sessão; reiniciar não zera (RN-18, RN-19)
 * @param ultimasMensagens o que a tela mostra, até {@link #MENSAGENS_GUARDADAS}, da mais antiga para a
 *     mais recente
 */
public record EstadoDaSessao(
        long sessaoId,
        StatusDaSessao status,
        MotivoDaPausa motivoDaPausa,
        Instant iniciadaEm,
        Duration tempoEmPausa,
        Instant pausadaDesde,
        Instant reiniciadaEm,
        int perguntasDaTurma,
        List<MensagemExibida> ultimasMensagens) {

    /** Quantas mensagens a tela reconstrói. As anteriores continuam no banco. */
    public static final int MENSAGENS_GUARDADAS = 20;

    public EstadoDaSessao {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(iniciadaEm, "iniciadaEm");
        Objects.requireNonNull(tempoEmPausa, "tempoEmPausa");
        if (perguntasDaTurma < 0) {
            throw new IllegalArgumentException("O contador de perguntas não pode ser negativo");
        }
        ultimasMensagens = ultimas(ultimasMensagens);
    }

    /** A sessão acabou de começar: em andamento, sem pausas nem mensagens. */
    public static EstadoDaSessao inicial(Sessao sessao) {
        Objects.requireNonNull(sessao.id(), "A sessão precisa estar gravada");
        return new EstadoDaSessao(
                sessao.id(), StatusDaSessao.EM_ANDAMENTO, null, sessao.iniciadaEm(), Duration.ZERO, null, null, 0, List.of());
    }

    /** RNF-04: refaz o estado a partir do banco, aplicando os eventos na ordem em que aconteceram. */
    public static EstadoDaSessao reconstruir(HistoricoDaSessao historico) {
        EstadoDaSessao estado = inicial(historico.sessao());
        for (EventoDaSessao evento : historico.eventos()) {
            estado = estado.aplicar(evento);
        }
        estado = new EstadoDaSessao(
                estado.sessaoId,
                estado.status,
                estado.motivoDaPausa,
                estado.iniciadaEm,
                estado.tempoEmPausa,
                estado.pausadaDesde,
                estado.reiniciadaEm,
                historico.perguntasDaTurma(),
                historico.mensagensDesdeOReinicio());
        Sessao sessao = historico.sessao();
        return sessao.status() == StatusDaSessao.ENCERRADA ? estado.encerrar(sessao.encerradaEm()) : estado;
    }

    /** Pausa, retomada ou reinício. */
    public EstadoDaSessao aplicar(EventoDaSessao evento) {
        return switch (evento.tipo()) {
            case PAUSA -> new EstadoDaSessao(
                    sessaoId, StatusDaSessao.PAUSADA, evento.motivo(), iniciadaEm, tempoEmPausa, evento.em(),
                    reiniciadaEm, perguntasDaTurma, ultimasMensagens);
            case RETOMADA -> new EstadoDaSessao(
                    sessaoId, StatusDaSessao.EM_ANDAMENTO, null, iniciadaEm, tempoEmPausaAte(evento.em()), null,
                    reiniciadaEm, perguntasDaTurma, ultimasMensagens);
            // RN-19: a tela fica limpa, mas o contador e o registro no banco continuam.
            case REINICIO -> new EstadoDaSessao(
                    sessaoId, status, motivoDaPausa, iniciadaEm, tempoEmPausa, pausadaDesde, evento.em(),
                    perguntasDaTurma, List.of());
        };
    }

    /** Uma pausa em curso termina no encerramento e entra no tempo em pausa. */
    public EstadoDaSessao encerrar(Instant em) {
        return new EstadoDaSessao(
                sessaoId, StatusDaSessao.ENCERRADA, null, iniciadaEm, tempoEmPausaAte(em), null, reiniciadaEm,
                perguntasDaTurma, ultimasMensagens);
    }

    /** Uma pergunta da turma ou uma resposta da IA apareceu na tela. Só a pergunta conta (RN-18). */
    public EstadoDaSessao registrarMensagem(MensagemExibida mensagem) {
        List<MensagemExibida> mensagens = new ArrayList<>(ultimasMensagens);
        mensagens.add(mensagem);
        int perguntas = perguntasDaTurma + (mensagem.autoria() == MensagemExibida.Autoria.TURMA ? 1 : 0);
        return new EstadoDaSessao(
                sessaoId, status, motivoDaPausa, iniciadaEm, tempoEmPausa, pausadaDesde, reiniciadaEm, perguntas,
                mensagens);
    }

    /** Soma das pausas até o momento dado, contando a pausa em curso. */
    public Duration tempoEmPausaAte(Instant momento) {
        return pausadaDesde == null ? tempoEmPausa : tempoEmPausa.plus(Duration.between(pausadaDesde, momento));
    }

    private static List<MensagemExibida> ultimas(List<MensagemExibida> mensagens) {
        int inicio = Math.max(0, mensagens.size() - MENSAGENS_GUARDADAS);
        return List.copyOf(mensagens.subList(inicio, mensagens.size()));
    }
}
