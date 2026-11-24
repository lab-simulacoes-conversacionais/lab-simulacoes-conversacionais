package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CursorDoLogTest {

    private static final Instant AGORA = Instant.parse("2026-11-08T13:45:10Z");

    @Test
    void cursorApontaParaALinhaGravada() {
        var linha = new RegistroDeAuditoria(9L, 7, AcaoCritica.APROVAR_CENARIO, "1", Map.of(), AGORA, "h");

        assertThat(CursorDoLog.apos(linha)).isEqualTo(new CursorDoLog(AGORA, 9));
    }

    @Test
    void linhaAindaNaoGravadaNaoViraCursor() {
        var linha = RegistroDeAuditoria.novo(AcaoCritica.APROVAR_CENARIO, 7, "1", Map.of(), AGORA);

        assertThatThrownBy(() -> CursorDoLog.apos(linha)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cursorSemDataERecusado() {
        assertThatThrownBy(() -> new CursorDoLog(null, 9)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void cursorComIdInvalidoERecusado(long id) {
        assertThatThrownBy(() -> new CursorDoLog(AGORA, id)).isInstanceOf(IllegalArgumentException.class);
    }
}
