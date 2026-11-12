package br.ufg.inf.labsimulacoes.sessoes.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida.Autoria;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Estado quente da sessão (Stack, seção 5) e sua reconstrução a partir do banco (RNF-04). */
class EstadoDaSessaoTest {

    private static final Instant INICIO = Instant.parse("2026-11-12T13:00:00Z");

    @Test
    void estadoInicialEmAndamentoSemPausasNemMensagens() {
        EstadoDaSessao estado = EstadoDaSessao.inicial(sessao());

        assertThat(estado.sessaoId()).isEqualTo(10);
        assertThat(estado.status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        assertThat(estado.iniciadaEm()).isEqualTo(INICIO);
        assertThat(estado.tempoEmPausa()).isZero();
        assertThat(estado.perguntasDaTurma()).isZero();
        assertThat(estado.ultimasMensagens()).isEmpty();
    }

    @Test
    void ultimaPerguntaDaTurmaSemRespostaEstaPendente() {
        var pergunta = new MensagemExibida(Autoria.TURMA, "Quem é você?", segundos(10));

        EstadoDaSessao estado = EstadoDaSessao.inicial(sessao()).registrarMensagem(pergunta);

        assertThat(estado.perguntaPendente()).contains(pergunta);
    }

    @Test
    void perguntaRespondidaNaoEstaPendente() {
        EstadoDaSessao estado = EstadoDaSessao.inicial(sessao())
                .registrarMensagem(new MensagemExibida(Autoria.TURMA, "Quem é você?", segundos(10)))
                .registrarMensagem(new MensagemExibida(Autoria.IA, "Sou Santos Dumont.", segundos(12)));

        assertThat(estado.perguntaPendente()).isEmpty();
        assertThat(EstadoDaSessao.inicial(sessao()).perguntaPendente()).isEmpty();
    }

    /** RN-20: a pergunta cuja resposta foi bloqueada continua na tela, mas não espera mais resposta. */
    @Test
    void perguntaComRespostaBloqueadaNaoEstaPendente() {
        var pergunta = new MensagemExibida(Autoria.TURMA, "Quem é você?", segundos(10), true);

        EstadoDaSessao estado = EstadoDaSessao.inicial(sessao()).registrarMensagem(pergunta);

        assertThat(estado.perguntaPendente()).isEmpty();
        assertThat(estado.ultimasMensagens()).containsExactly(pergunta);
        assertThat(estado.perguntasDaTurma()).isEqualTo(1);
    }

    /** A pergunta pendente vai à parte no pedido, então não se repete no histórico. */
    @Test
    void historicoParaOModeloNaoRepeteAPerguntaPendente() {
        EstadoDaSessao estado = EstadoDaSessao.inicial(sessao())
                .registrarMensagem(turma("Quem é você?", 10))
                .registrarMensagem(ia("Sou Santos Dumont.", 12))
                .registrarMensagem(turma("E o 14-bis?", 20));

        assertThat(estado.historicoParaOModelo())
                .containsExactly(turma("Quem é você?", 10), ia("Sou Santos Dumont.", 12));
        assertThat(estado.registrarMensagem(ia("Voou em 1906.", 22)).historicoParaOModelo()).hasSize(4);
    }

    /** RN-20: a pergunta cuja resposta foi bloqueada não volta ao modelo. */
    @Test
    void historicoParaOModeloDeixaDeForaAPerguntaBloqueada() {
        var bloqueada = new MensagemExibida(Autoria.TURMA, "Pergunta que gerou o bloqueio", segundos(20), true);
        EstadoDaSessao estado = EstadoDaSessao.inicial(sessao())
                .registrarMensagem(turma("Quem é você?", 10))
                .registrarMensagem(ia("Sou Santos Dumont.", 12))
                .registrarMensagem(bloqueada)
                .registrarMensagem(turma("E o 14-bis?", 30));

        assertThat(estado.historicoParaOModelo())
                .containsExactly(turma("Quem é você?", 10), ia("Sou Santos Dumont.", 12));
    }

    @Test
    void soAPerguntaDaTurmaTemARespostaBloqueada() {
        assertThatThrownBy(() -> new MensagemExibida(Autoria.IA, "Sou Santos Dumont.", segundos(10), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new MensagemExibida(Autoria.IA, "Sou Santos Dumont.", segundos(10)).respostaBloqueada()).isFalse();
    }

    /** RN-19: depois de reiniciar, a tela está limpa e nada fica pendente. */
    @Test
    void reinicioLimpaAPerguntaPendente() {
        EstadoDaSessao estado = EstadoDaSessao.inicial(sessao())
                .registrarMensagem(new MensagemExibida(Autoria.TURMA, "Quem é você?", segundos(10)))
                .aplicar(EventoDaSessao.reinicio(segundos(20)));

        assertThat(estado.perguntaPendente()).isEmpty();
    }

    @Test
    void sessaoAindaNaoGravadaNaoTemEstado() {
        assertThatThrownBy(() -> EstadoDaSessao.inicial(Sessao.iniciar(1, 2, 3, 40, INICIO)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void pausaERetomadaSomamOTempoEmPausa() {
        EstadoDaSessao estado = EstadoDaSessao.inicial(sessao())
                .aplicar(EventoDaSessao.pausa(MotivoDaPausa.MANUAL, segundos(60)));

        assertThat(estado.status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(estado.motivoDaPausa()).isEqualTo(MotivoDaPausa.MANUAL);
        assertThat(estado.pausadaDesde()).isEqualTo(segundos(60));
        assertThat(estado.tempoEmPausaAte(segundos(90))).isEqualTo(Duration.ofSeconds(30));

        estado = estado.aplicar(EventoDaSessao.retomada(segundos(100)))
                .aplicar(EventoDaSessao.pausa(MotivoDaPausa.CONTEUDO_BLOQUEADO, segundos(200)))
                .aplicar(EventoDaSessao.retomada(segundos(210)));

        assertThat(estado.status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        assertThat(estado.motivoDaPausa()).isNull();
        assertThat(estado.pausadaDesde()).isNull();
        assertThat(estado.tempoEmPausa()).isEqualTo(Duration.ofSeconds(50));
    }

    /** RN-19: reiniciar limpa a tela, mas o contador de perguntas continua. */
    @Test
    void reinicioLimpaATelaEMantemOContador() {
        EstadoDaSessao estado = EstadoDaSessao.inicial(sessao())
                .registrarMensagem(turma("Por que o 14-bis?", 10))
                .registrarMensagem(ia("Porque voou sozinho.", 11))
                .aplicar(EventoDaSessao.reinicio(segundos(20)));

        assertThat(estado.ultimasMensagens()).isEmpty();
        assertThat(estado.perguntasDaTurma()).isEqualTo(1);
        assertThat(estado.reiniciadaEm()).isEqualTo(segundos(20));
        assertThat(estado.status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
    }

    @Test
    void soAPerguntaDaTurmaContaEATelaGuardaAsUltimas() {
        EstadoDaSessao estado = EstadoDaSessao.inicial(sessao());
        for (int i = 0; i < EstadoDaSessao.MENSAGENS_GUARDADAS + 5; i++) {
            estado = estado.registrarMensagem(i % 2 == 0 ? turma("P" + i, i) : ia("R" + i, i));
        }

        assertThat(estado.perguntasDaTurma()).isEqualTo(13);
        assertThat(estado.ultimasMensagens()).hasSize(EstadoDaSessao.MENSAGENS_GUARDADAS);
        assertThat(estado.ultimasMensagens().get(0).texto()).isEqualTo("R5");
        assertThat(estado.ultimasMensagens().get(EstadoDaSessao.MENSAGENS_GUARDADAS - 1).texto()).isEqualTo("P24");
    }

    /** Uma pausa em curso termina no encerramento e entra no tempo em pausa (RN-28). */
    @Test
    void encerrarDuranteAPausaFechaAPausa() {
        EstadoDaSessao estado = EstadoDaSessao.inicial(sessao())
                .aplicar(EventoDaSessao.pausa(MotivoDaPausa.MANUAL, segundos(60)))
                .encerrar(segundos(100));

        assertThat(estado.status()).isEqualTo(StatusDaSessao.ENCERRADA);
        assertThat(estado.motivoDaPausa()).isNull();
        assertThat(estado.pausadaDesde()).isNull();
        assertThat(estado.tempoEmPausa()).isEqualTo(Duration.ofSeconds(40));
    }

    /** RNF-04: reconstruir do banco chega ao mesmo estado que a atualização a cada interação. */
    @Test
    void reconstrucaoChegaAoMesmoEstadoDaAtualizacao() {
        List<EventoDaSessao> eventos = List.of(
                EventoDaSessao.pausa(MotivoDaPausa.MANUAL, segundos(60)),
                EventoDaSessao.retomada(segundos(90)),
                EventoDaSessao.reinicio(segundos(120)),
                EventoDaSessao.pausa(MotivoDaPausa.LIMITE_50_MINUTOS, segundos(3000)));
        EstadoDaSessao atualizado = EstadoDaSessao.inicial(sessao());
        for (EventoDaSessao evento : eventos) {
            atualizado = atualizado.aplicar(evento);
        }
        atualizado = atualizado.registrarMensagem(turma("Depois do reinício", 200));
        Sessao pausada = new Sessao(10L, 1, 2, 3, 40, StatusDaSessao.PAUSADA, MotivoDaPausa.LIMITE_50_MINUTOS, INICIO, null);

        EstadoDaSessao reconstruido = EstadoDaSessao.reconstruir(
                new HistoricoDaSessao(pausada, eventos, 1, List.of(turma("Depois do reinício", 200))));

        assertThat(reconstruido).isEqualTo(atualizado);
    }

    @Test
    void reconstrucaoDaSessaoEncerradaFechaAPausa() {
        Sessao encerrada = new Sessao(10L, 1, 2, 3, 40, StatusDaSessao.ENCERRADA, null, INICIO, segundos(500));

        EstadoDaSessao estado = EstadoDaSessao.reconstruir(new HistoricoDaSessao(
                encerrada, List.of(EventoDaSessao.pausa(MotivoDaPausa.MANUAL, segundos(400))), 0, List.of()));

        assertThat(estado.status()).isEqualTo(StatusDaSessao.ENCERRADA);
        assertThat(estado.tempoEmPausa()).isEqualTo(Duration.ofSeconds(100));
    }

    @Test
    void estadoGuardaNoMaximoAsUltimasMensagens() {
        List<MensagemExibida> muitas = new ArrayList<>();
        IntStream.range(0, EstadoDaSessao.MENSAGENS_GUARDADAS + 1).forEach(i -> muitas.add(ia("R" + i, i)));

        EstadoDaSessao estado = new EstadoDaSessao(
                10, StatusDaSessao.EM_ANDAMENTO, null, INICIO, Duration.ZERO, null, null, 0, muitas);

        assertThat(estado.ultimasMensagens()).hasSize(EstadoDaSessao.MENSAGENS_GUARDADAS);
        assertThat(estado.ultimasMensagens().get(0).texto()).isEqualTo("R1");
    }

    @Test
    void contadorNegativoERecusado() {
        assertThatThrownBy(() -> new EstadoDaSessao(
                        10, StatusDaSessao.EM_ANDAMENTO, null, INICIO, Duration.ZERO, null, null, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Sessao sessao() {
        return Sessao.iniciar(1, 2, 3, 40, INICIO).comId(10);
    }

    private static Instant segundos(long s) {
        return INICIO.plusSeconds(s);
    }

    private static MensagemExibida turma(String texto, long s) {
        return new MensagemExibida(Autoria.TURMA, texto, segundos(s));
    }

    private static MensagemExibida ia(String texto, long s) {
        return new MensagemExibida(Autoria.IA, texto, segundos(s));
    }
}
