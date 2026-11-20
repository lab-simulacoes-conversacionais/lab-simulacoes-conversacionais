package br.ufg.inf.labsimulacoes.metricas.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** O dia do custo da IA no fuso da escola (RNF-13), sem banco nem Spring. */
class DiaDeConsumoTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    void diaVaiDaMeiaNoiteAMeiaNoiteNoFuso() {
        DiaDeConsumo dia = DiaDeConsumo.de(Instant.parse("2026-11-19T15:00:00Z"), SAO_PAULO);

        assertThat(dia.dia()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(dia.inicio()).isEqualTo(Instant.parse("2026-11-19T03:00:00Z"));
        assertThat(dia.fim()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
    }

    /** À meia-noite de São Paulo, 03h em UTC, o dia vira; um instante antes ainda é o dia anterior. */
    @Test
    void diaViraAMeiaNoiteNoFusoENaoEmUtc() {
        assertThat(DiaDeConsumo.de(Instant.parse("2026-11-20T02:59:59.999Z"), SAO_PAULO).dia())
                .isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(DiaDeConsumo.de(Instant.parse("2026-11-20T03:00:00Z"), SAO_PAULO).dia())
                .isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(DiaDeConsumo.de(Instant.parse("2026-11-20T01:00:00Z"), ZoneId.of("UTC")).dia())
                .isEqualTo(LocalDate.of(2026, 11, 20));
    }

    /** O fim de um dia é o início do seguinte: nenhum instante fica em dois dias nem em nenhum. */
    @Test
    void diasSeguidosSeEncostam() {
        DiaDeConsumo hoje = DiaDeConsumo.de(Instant.parse("2026-11-19T12:00:00Z"), SAO_PAULO);
        DiaDeConsumo amanha = DiaDeConsumo.de(hoje.fim(), SAO_PAULO);

        assertThat(amanha.inicio()).isEqualTo(hoje.fim());
        assertThat(amanha.dia()).isEqualTo(hoje.dia().plusDays(1));
    }

    /** Num fuso com horário de verão, o dia da mudança tem 23 horas. */
    @Test
    void diaDaMudancaDeHorarioTemOutraDuracao() {
        DiaDeConsumo mudanca = DiaDeConsumo.de(Instant.parse("2026-03-08T12:00:00Z"), ZoneId.of("America/New_York"));

        assertThat(Duration.between(mudanca.inicio(), mudanca.fim())).isEqualTo(Duration.ofHours(23));
    }

    @Test
    void diaPrecisaComecarAntesDeTerminar() {
        Instant instante = Instant.parse("2026-11-19T03:00:00Z");

        assertThatThrownBy(() -> new DiaDeConsumo(LocalDate.of(2026, 11, 19), instante, instante))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiaDeConsumo.de(null, SAO_PAULO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DiaDeConsumo.de(instante, null)).isInstanceOf(NullPointerException.class);
    }
}
