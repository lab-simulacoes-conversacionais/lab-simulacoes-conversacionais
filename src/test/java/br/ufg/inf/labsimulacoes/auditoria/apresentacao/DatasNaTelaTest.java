package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** As datas da fila de triagem e do log no fuso da escola, como o protótipo mostra. */
class DatasNaTelaTest {

    private final DatasNaTela datas = new DatasNaTela(ZoneId.of("America/Sao_Paulo"));

    @Test
    void mesAbreviadoSemPonto() {
        Instant quando = Instant.parse("2026-06-14T13:05:00Z");

        assertThat(datas.dia(quando)).isEqualTo("14 jun");
        assertThat(datas.diaEHora(quando)).isEqualTo("14 jun · 10:05");
        assertThat(datas.completo(quando)).isEqualTo("14 jun 2026 · 10:05");
    }

    @Test
    void limiteComHorarioMostraODiaEAHora() {
        assertThat(datas.limite(Instant.parse("2026-11-24T18:00:00Z"))).isEqualTo("24 nov · 15:00");
    }

    /** RN-24: a de sábado vence na quarta à 0h, que a tela mostra como o fim da terça. */
    @Test
    void limiteAMeiaNoiteMostraOFimDoDiaAnterior() {
        Instant quartaZeroHora = Instant.parse("2026-11-25T03:00:00Z");

        assertThat(datas.limite(quartaZeroHora)).isEqualTo("24 nov · fim do dia");
    }

    @Test
    void inicioDoDiaNoFusoDaEscola() {
        assertThat(datas.inicioDo(LocalDate.parse("2026-11-26"))).isEqualTo(Instant.parse("2026-11-26T03:00:00Z"));
    }

    /**
     * O fuso vem da mesma propriedade do prazo (auditoria.triagem.fuso). Em Manaus, a meia-noite da escola é
     * 1h em Brasília, e a tela ainda mostra o fim do dia anterior, como o domínio calcula.
     */
    @Test
    void usaOFusoConfiguradoDaEscola() {
        var manaus = new DatasNaTela(ZoneId.of("America/Manaus"));
        Instant quartaZeroHoraEmManaus = Instant.parse("2026-11-25T04:00:00Z");

        assertThat(manaus.limite(quartaZeroHoraEmManaus)).isEqualTo("24 nov · fim do dia");
        assertThat(datas.limite(quartaZeroHoraEmManaus)).isEqualTo("25 nov · 01:00");
        assertThat(manaus.inicioDo(LocalDate.parse("2026-11-26"))).isEqualTo(Instant.parse("2026-11-26T04:00:00Z"));
    }
}
