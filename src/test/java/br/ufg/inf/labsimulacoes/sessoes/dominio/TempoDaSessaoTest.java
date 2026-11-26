package br.ufg.inf.labsimulacoes.sessoes.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tempo de conversa e pausa automática aos 50 minutos (RF-19, RN-17), sem banco nem tela. */
class TempoDaSessaoTest {

    private static final Instant INICIO = Instant.parse("2026-11-23T13:00:00Z");

    // Tempo de conversa

    @Test
    void tempoDeConversaEOTempoDesdeOInicio() {
        assertThat(inicial().tempoDeConversaAte(minutos(12))).isEqualTo(Duration.ofMinutes(12));
    }

    @Test
    void pausasJaRetomadasNaoContam() {
        EstadoDaSessao estado = inicial()
                .aplicar(EventoDaSessao.pausa(MotivoDaPausa.MANUAL, minutos(10)))
                .aplicar(EventoDaSessao.retomada(minutos(25)));

        assertThat(estado.tempoDeConversaAte(minutos(30))).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void naPausaOTempoParaDeContar() {
        EstadoDaSessao pausada = inicial().aplicar(EventoDaSessao.pausa(MotivoDaPausa.MANUAL, minutos(10)));

        assertThat(pausada.tempoDeConversaAte(minutos(40))).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void tempoNuncaENegativo() {
        assertThat(inicial().tempoDeConversaAte(INICIO.minusSeconds(5))).isEqualTo(Duration.ZERO);
    }

    // Pausa aos 50 minutos (RN-17)

    @Test
    void antesDosCinquentaMinutosNaoPausa() {
        assertThat(emAndamento().pausarNoLimiteDeTempo(inicial(), minutos(49).plusSeconds(59))).isEmpty();
    }

    @Test
    void aosCinquentaMinutosPausaComOMotivo() {
        MudancaNaSessao pausa = emAndamento().pausarNoLimiteDeTempo(inicial(), minutos(50)).orElseThrow();

        assertThat(pausa.sessao().status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(pausa.sessao().motivoDaPausa()).isEqualTo(MotivoDaPausa.LIMITE_50_MINUTOS);
        assertThat(pausa.evento()).isEqualTo(EventoDaSessao.pausa(MotivoDaPausa.LIMITE_50_MINUTOS, minutos(50)));
    }

    /** Conferida depois, a pausa fica no momento em que a conversa completou 50 minutos. */
    @Test
    void pausaConferidaDepoisFicaNoMomentoDosCinquentaMinutos() {
        MudancaNaSessao pausa = emAndamento().pausarNoLimiteDeTempo(inicial(), minutos(58)).orElseThrow();

        assertThat(pausa.evento().em()).isEqualTo(minutos(50));
        EstadoDaSessao depois = inicial().aplicar(pausa.evento());
        assertThat(depois.tempoDeConversaAte(minutos(58))).isEqualTo(Sessao.LIMITE_DE_TEMPO);
    }

    /** São 50 minutos de conversa: as pausas empurram o limite para mais tarde. */
    @Test
    void pausasEmpurramOLimite() {
        EstadoDaSessao comPausa = inicial()
                .aplicar(EventoDaSessao.pausa(MotivoDaPausa.MANUAL, minutos(20)))
                .aplicar(EventoDaSessao.retomada(minutos(30)));
        Sessao sessao = emAndamento();

        assertThat(sessao.pausarNoLimiteDeTempo(comPausa, minutos(55))).isEmpty();
        assertThat(sessao.pausarNoLimiteDeTempo(comPausa, minutos(65)).orElseThrow().evento().em())
                .isEqualTo(minutos(60));
    }

    /**
     * A pausa conferida tarde nunca fica antes do que já foi registrado: o histórico é refeito em ordem
     * de data. Aqui o professor pausou aos 52 minutos e retomou aos 60, e ninguém tinha conferido.
     */
    @Test
    void pausaConferidaTardeNaoFicaAntesDoUltimoEvento() {
        EstadoDaSessao estado = inicial()
                .aplicar(EventoDaSessao.pausa(MotivoDaPausa.MANUAL, minutos(52)))
                .aplicar(EventoDaSessao.retomada(minutos(60)));

        MudancaNaSessao pausa = emAndamento().pausarNoLimiteDeTempo(estado, minutos(61)).orElseThrow();

        assertThat(pausa.evento().em()).isEqualTo(minutos(60));
        assertThat(estado.ultimoEventoEm()).isEqualTo(minutos(60));
    }

    /** Uma pergunta gravada logo depois dos 50 minutos também não fica dentro da pausa. */
    @Test
    void pausaConferidaTardeNaoFicaAntesDaUltimaMensagem() {
        EstadoDaSessao estado = inicial().registrarMensagem(
                new MensagemExibida(MensagemExibida.Autoria.TURMA, "Como o 14-bis voou?", minutos(50).plusSeconds(3)));

        MudancaNaSessao pausa = emAndamento().pausarNoLimiteDeTempo(estado, minutos(51)).orElseThrow();

        assertThat(pausa.evento().em()).isEqualTo(minutos(50).plusSeconds(3));
    }

    @Test
    void ultimaAtividadeEOEventoOuAMensagemMaisRecente() {
        assertThat(inicial().ultimaAtividade()).isEmpty();
        EstadoDaSessao comPausa = inicial().aplicar(EventoDaSessao.pausa(MotivoDaPausa.MANUAL, minutos(5)));
        assertThat(comPausa.ultimaAtividade()).contains(minutos(5));
        EstadoDaSessao comMensagemAntes = comPausa.registrarMensagem(
                new MensagemExibida(MensagemExibida.Autoria.IA, "Olá.", minutos(4)));
        assertThat(comMensagemAntes.ultimaAtividade()).contains(minutos(5));
        EstadoDaSessao soComMensagem = inicial().registrarMensagem(
                new MensagemExibida(MensagemExibida.Autoria.TURMA, "Oi?", minutos(2)));
        assertThat(soComMensagem.ultimaAtividade()).contains(minutos(2));
    }

    /** Depois que o professor confirma e retoma, a sessão não pausa de novo pelo tempo. */
    @Test
    void depoisDeRetomarNaoPausaDeNovoPeloTempo() {
        EstadoDaSessao retomada = inicial()
                .aplicar(EventoDaSessao.pausa(MotivoDaPausa.LIMITE_50_MINUTOS, minutos(50)))
                .aplicar(EventoDaSessao.retomada(minutos(52)));

        assertThat(retomada.limiteDeTempoAtingido()).isTrue();
        assertThat(emAndamento().pausarNoLimiteDeTempo(retomada, minutos(90))).isEmpty();
    }

    @Test
    void outrasPausasNaoMarcamOLimite() {
        EstadoDaSessao estado = inicial()
                .aplicar(EventoDaSessao.pausa(MotivoDaPausa.CONTEUDO_BLOQUEADO, minutos(5)))
                .aplicar(EventoDaSessao.retomada(minutos(6)));

        assertThat(estado.limiteDeTempoAtingido()).isFalse();
    }

    @Test
    void pausadaOuEncerradaNaoPausaPeloTempo() {
        Sessao pausada = emAndamento().pausar(MotivoDaPausa.MANUAL, minutos(40)).sessao();
        Sessao encerrada = emAndamento().encerrar(minutos(40));

        assertThat(pausada.pausarNoLimiteDeTempo(inicial(), minutos(90))).isEmpty();
        assertThat(encerrada.pausarNoLimiteDeTempo(inicial(), minutos(90))).isEmpty();
    }

    /** RNF-04: a marca do limite volta na reconstrução a partir do banco. */
    @Test
    void reconstrucaoGuardaAMarcaDoLimite() {
        var historico = new HistoricoDaSessao(
                emAndamento(),
                List.of(EventoDaSessao.pausa(MotivoDaPausa.LIMITE_50_MINUTOS, minutos(50)),
                        EventoDaSessao.retomada(minutos(51))),
                0,
                List.of());

        EstadoDaSessao reconstruido = EstadoDaSessao.reconstruir(historico);

        assertThat(reconstruido.limiteDeTempoAtingido()).isTrue();
        assertThat(reconstruido.encerrar(minutos(60)).limiteDeTempoAtingido()).isTrue();
    }

    private static Sessao emAndamento() {
        return Sessao.iniciar(1, 2, 3, 40, INICIO).comId(5);
    }

    private static EstadoDaSessao inicial() {
        return EstadoDaSessao.inicial(emAndamento());
    }

    private static Instant minutos(long minutos) {
        return INICIO.plus(Duration.ofMinutes(minutos));
    }
}
