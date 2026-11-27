package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * O prazo da triagem (RN-24): 2 dias úteis, de segunda a sexta, no fuso da escola, com o limite incluído.
 * Novembro de 2026: o dia 23 é uma segunda-feira.
 */
class PrazoDaTriagemTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @ParameterizedTest(name = "[{index}] criada {0} → vence {1}")
    @CsvSource({
        // Segunda a quarta, no mesmo horário.
        "2026-11-23T10:00, 2026-11-25T10:00",
        // Quarta a sexta.
        "2026-11-25T16:30, 2026-11-27T16:30",
        // Quinta vence na segunda: pula o fim de semana.
        "2026-11-26T18:00, 2026-11-30T18:00",
        // Sexta vence na terça.
        "2026-11-27T15:00, 2026-12-01T15:00",
        // Sábado e domingo contam do início da segunda: vencem na quarta às 0h, no fim da terça.
        "2026-11-28T10:00, 2026-12-02T00:00",
        "2026-11-29T23:59, 2026-12-02T00:00",
        // Sexta à noite no fuso da escola, já sábado em UTC: vale o dia da escola.
        "2026-11-27T22:30, 2026-12-01T22:30"
    })
    void venceDepoisDeDoisDiasUteis(String criada, String vence) {
        assertThat(PrazoDaTriagem.limite(naEscola(criada), FUSO)).isEqualTo(naEscola(vence));
    }

    @Test
    void limiteAindaEstaNoPrazoEOInstanteSeguinteNao() {
        Instant criada = naEscola("2026-11-23T10:00");
        Instant limite = PrazoDaTriagem.limite(criada, FUSO);

        assertThat(PrazoDaTriagem.foraDoPrazo(criada, criada, FUSO)).isFalse();
        assertThat(PrazoDaTriagem.foraDoPrazo(criada, limite, FUSO)).isFalse();
        assertThat(PrazoDaTriagem.foraDoPrazo(criada, limite.plusNanos(1_000), FUSO)).isTrue();
        assertThat(PrazoDaTriagem.foraDoPrazo(criada, limite.plus(Duration.ofDays(3)), FUSO)).isTrue();
    }

    /** O fim de semana não conta: a de sexta ainda está no prazo na segunda à noite. */
    @Test
    void fimDeSemanaNaoConsomeOPrazo() {
        Instant sexta = naEscola("2026-11-27T15:00");

        assertThat(PrazoDaTriagem.foraDoPrazo(sexta, naEscola("2026-11-30T23:00"), FUSO)).isFalse();
        assertThat(PrazoDaTriagem.foraDoPrazo(sexta, naEscola("2026-12-01T15:01"), FUSO)).isTrue();
    }

    @Test
    void momentosEFusoSaoObrigatorios() {
        Instant criada = naEscola("2026-11-23T10:00");

        assertThatThrownBy(() -> PrazoDaTriagem.limite(null, FUSO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrazoDaTriagem.limite(criada, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrazoDaTriagem.foraDoPrazo(criada, null, FUSO))
                .isInstanceOf(NullPointerException.class);
    }

    private static Instant naEscola(String momento) {
        return LocalDateTime.parse(momento).atZone(FUSO).toInstant();
    }
}
