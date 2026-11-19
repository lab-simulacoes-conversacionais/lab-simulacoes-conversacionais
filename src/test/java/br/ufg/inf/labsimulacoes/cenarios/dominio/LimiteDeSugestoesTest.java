package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** RNF-13: limite de pedidos de sugestão de cenário por usuário, sem Redis nem Spring. */
class LimiteDeSugestoesTest {

    private static final LimiteDeSugestoes DEZ_POR_HORA = new LimiteDeSugestoes(10, Duration.ofHours(1));

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 10})
    void pedidosAteOLimitePassam(long pedidos) {
        assertThatCode(() -> DEZ_POR_HORA.exigirDentro(pedidos)).doesNotThrowAnyException();
    }

    @Test
    void pedidoAlemDoLimiteERecusado() {
        assertThatThrownBy(() -> DEZ_POR_HORA.exigirDentro(11))
                .isInstanceOf(LimiteDeSugestoesAtingido.class)
                .hasMessageContaining("10 sugestões")
                .hasMessageContaining("60 minutos");
    }

    @Test
    void limitePrecisaAceitarAoMenosUmPedido() {
        assertThatThrownBy(() -> new LimiteDeSugestoes(0, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void janelaPrecisaSerPositiva() {
        assertThatThrownBy(() -> new LimiteDeSugestoes(10, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimiteDeSugestoes(10, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimiteDeSugestoes(10, null)).isInstanceOf(NullPointerException.class);
    }
}
