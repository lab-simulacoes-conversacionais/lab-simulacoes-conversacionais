package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** As datas da fila de triagem e do log no fuso da escola, como o protótipo mostra. */
class DatasNaTelaTest {

    @Test
    void mesAbreviadoSemPonto() {
        Instant quando = Instant.parse("2026-06-14T13:05:00Z");

        assertThat(DatasNaTela.dia(quando)).isEqualTo("14 jun");
        assertThat(DatasNaTela.diaEHora(quando)).isEqualTo("14 jun · 10:05");
        assertThat(DatasNaTela.completo(quando)).isEqualTo("14 jun 2026 · 10:05");
    }

    @Test
    void limiteComHorarioMostraODiaEAHora() {
        assertThat(DatasNaTela.limite(Instant.parse("2026-11-24T18:00:00Z"))).isEqualTo("24 nov · 15:00");
    }

    /** RN-24: a de sábado vence na quarta à 0h, que a tela mostra como o fim da terça. */
    @Test
    void limiteAMeiaNoiteMostraOFimDoDiaAnterior() {
        Instant quartaZeroHora = Instant.parse("2026-11-25T03:00:00Z");

        assertThat(DatasNaTela.limite(quartaZeroHora)).isEqualTo("24 nov · fim do dia");
    }

    @Test
    void inicioDoDiaNoFusoDaEscola() {
        assertThat(DatasNaTela.inicioDo(LocalDate.parse("2026-11-26"))).isEqualTo(Instant.parse("2026-11-26T03:00:00Z"));
    }
}
