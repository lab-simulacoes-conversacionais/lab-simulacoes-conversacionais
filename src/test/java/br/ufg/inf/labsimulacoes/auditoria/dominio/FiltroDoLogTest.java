package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FiltroDoLogTest {

    private static final Instant INICIO = Instant.parse("2026-11-01T03:00:00Z");
    private static final Instant FIM = Instant.parse("2026-11-08T03:00:00Z");

    @Test
    void todosOsFiltrosSaoOpcionais() {
        var filtro = new FiltroDoLog(null, null, null, null, 0, 20);

        assertThat(filtro.inicio()).isNull();
        assertThat(filtro.fim()).isNull();
        assertThat(filtro.usuarioId()).isNull();
        assertThat(filtro.acao()).isNull();
    }

    @Test
    void filtrosSeCombinam() {
        var filtro = new FiltroDoLog(INICIO, FIM, 7L, AcaoCritica.APROVAR_CENARIO, 0, 20);

        assertThat(filtro.inicio()).isEqualTo(INICIO);
        assertThat(filtro.fim()).isEqualTo(FIM);
        assertThat(filtro.usuarioId()).isEqualTo(7L);
        assertThat(filtro.acao()).isEqualTo(AcaoCritica.APROVAR_CENARIO);
    }

    @Test
    void periodoPodeTerSoUmaDasPontas() {
        assertThat(new FiltroDoLog(INICIO, null, null, null, 0, 20).inicio()).isEqualTo(INICIO);
        assertThat(new FiltroDoLog(null, FIM, null, null, 0, 20).fim()).isEqualTo(FIM);
    }

    @Test
    void inicioDepoisDoFimERecusado() {
        assertThatThrownBy(() -> new FiltroDoLog(FIM, INICIO, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("período");
    }

    @Test
    void periodoVazioERecusado() {
        assertThatThrownBy(() -> new FiltroDoLog(INICIO, INICIO, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void usuarioInvalidoERecusado(long usuarioId) {
        assertThatThrownBy(() -> new FiltroDoLog(null, null, usuarioId, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paginaNegativaERecusada() {
        assertThatThrownBy(() -> new FiltroDoLog(null, null, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, FiltroDoLog.TAMANHO_MAXIMO + 1})
    void tamanhoForaDoLimiteERecusado(int tamanho) {
        assertThatThrownBy(() -> new FiltroDoLog(null, null, null, null, 0, tamanho))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deslocamentoPulaAsPaginasAnteriores() {
        assertThat(new FiltroDoLog(null, null, null, null, 0, 20).deslocamento()).isZero();
        assertThat(new FiltroDoLog(null, null, null, null, 3, 20).deslocamento()).isEqualTo(60);
    }

    @Test
    void tudoEAPrimeiraPaginaSemFiltro() {
        var tudo = FiltroDoLog.tudo();

        assertThat(tudo).isEqualTo(new FiltroDoLog(null, null, null, null, 0, FiltroDoLog.TAMANHO_MAXIMO));
    }
}
