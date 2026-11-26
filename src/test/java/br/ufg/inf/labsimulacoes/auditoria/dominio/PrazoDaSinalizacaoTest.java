package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** O prazo do professor (RN-21): durante a sessão ou até 7 × 24 horas depois, com o limite incluído. */
class PrazoDaSinalizacaoTest {

    private static final Instant INICIO = Instant.parse("2026-11-20T13:00:00Z");
    private static final Instant ENCERRAMENTO = Instant.parse("2026-11-20T13:50:00Z");

    @Test
    void contaDoEncerramento() {
        assertThat(PrazoDaSinalizacao.base(INICIO, ENCERRAMENTO)).isEqualTo(ENCERRAMENTO);
    }

    /** Uma sessão que nunca foi encerrada, como uma pausada, conta do início. */
    @Test
    void semEncerramentoContaDoInicio() {
        assertThat(PrazoDaSinalizacao.base(INICIO, null)).isEqualTo(INICIO);
    }

    @Test
    void limiteEDeSeteVezesVinteEQuatroHoras() {
        assertThat(PrazoDaSinalizacao.limite(ENCERRAMENTO)).isEqualTo(Instant.parse("2026-11-27T13:50:00Z"));
        assertThat(PrazoDaSinalizacao.JANELA).isEqualTo(Duration.ofHours(7 * 24));
    }

    @Test
    void permiteDuranteEDepoisDaSessaoAteOLimiteIncluido() {
        Instant limite = PrazoDaSinalizacao.limite(ENCERRAMENTO);

        assertThat(PrazoDaSinalizacao.permite(ENCERRAMENTO, ENCERRAMENTO.minusSeconds(60))).isTrue();
        assertThat(PrazoDaSinalizacao.permite(ENCERRAMENTO, ENCERRAMENTO)).isTrue();
        assertThat(PrazoDaSinalizacao.permite(ENCERRAMENTO, limite.minusNanos(1_000))).isTrue();
        assertThat(PrazoDaSinalizacao.permite(ENCERRAMENTO, limite)).isTrue();
    }

    @Test
    void naoPermiteUmMicrossegundoDepoisDoLimite() {
        Instant limite = PrazoDaSinalizacao.limite(ENCERRAMENTO);

        assertThat(PrazoDaSinalizacao.permite(ENCERRAMENTO, limite.plusNanos(1_000))).isFalse();
        assertThat(PrazoDaSinalizacao.permite(ENCERRAMENTO, limite.plus(Duration.ofDays(1)))).isFalse();
    }

    /**
     * O limite que a tela mostra é o último instante que a regra aceita: {@code limite(base)} e
     * {@code permite(base, agora)} concordam no instante exato, até no nanossegundo, para qualquer base.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "2026-11-20T13:50:00Z",
        "2026-11-20T13:50:00.123456Z",
        "2026-02-22T23:59:59.999999999Z",
        "2026-10-31T23:30:00Z",
        "1970-01-01T00:00:00Z"
    })
    void limiteEPermiteConcordamNoInstanteExato(String momento) {
        Instant base = Instant.parse(momento);
        Instant limite = PrazoDaSinalizacao.limite(base);

        assertThat(PrazoDaSinalizacao.permite(base, limite)).isTrue();
        assertThat(PrazoDaSinalizacao.permite(base, limite.minusNanos(1))).isTrue();
        assertThat(PrazoDaSinalizacao.permite(base, limite.plusNanos(1))).isFalse();
        assertThat(Duration.between(base, limite)).isEqualTo(PrazoDaSinalizacao.JANELA);
    }

    @Test
    void sessaoPausadaHaMaisDeSeteDiasNaoPermite() {
        Instant base = PrazoDaSinalizacao.base(INICIO, null);

        assertThat(PrazoDaSinalizacao.permite(base, INICIO.plus(Duration.ofDays(7)))).isTrue();
        assertThat(PrazoDaSinalizacao.permite(base, INICIO.plus(Duration.ofDays(8)))).isFalse();
    }

    @Test
    void momentosSaoObrigatorios() {
        assertThatThrownBy(() -> PrazoDaSinalizacao.base(null, ENCERRAMENTO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrazoDaSinalizacao.limite(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrazoDaSinalizacao.permite(ENCERRAMENTO, null))
                .isInstanceOf(NullPointerException.class);
    }
}
