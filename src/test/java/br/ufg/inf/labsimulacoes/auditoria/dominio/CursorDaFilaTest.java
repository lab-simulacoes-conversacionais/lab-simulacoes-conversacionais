package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CursorDaFilaTest {

    private static final Instant CRIADA = Instant.parse("2026-11-23T12:00:00Z");

    @Test
    void continuaDepoisDaUltimaSinalizacao() {
        var sinalizacao = new SinalizacaoPendente(7, 2L, 3, "p", "r", TipoDeErro.ANACRONISMO,
                GravidadePercebida.LEVE, "j", PerfilDaSinalizacao.CURADOR, 4, CRIADA);

        assertThat(CursorDaFila.apos(sinalizacao)).isEqualTo(new CursorDaFila(CRIADA, 7));
    }

    @Test
    void precisaDoMomentoEDeUmaSinalizacaoGravada() {
        assertThatThrownBy(() -> new CursorDaFila(null, 7)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CursorDaFila(CRIADA, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
