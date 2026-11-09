package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.cenarios.ConsultaAoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.HabilidadeDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.BuscasDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.FiltroDeHabilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BuscaNoCatalogoTest {

    private final BuscasDoCatalogo buscas = mock(BuscasDoCatalogo.class);
    private final BuscaNoCatalogo catalogo = new BuscaNoCatalogo(buscas, 50);

    @Test
    void converteAConsultaNoFiltroNormalizadoComOLimitePadrao() {
        catalogo.buscar(ConsultaAoCatalogo.porCodigo(" ef08hi03 ").comComponente("HISTÓRIA").comAno(8));

        verify(buscas).buscar(new FiltroDeHabilidades("EF08HI03", null, null, 8, "história", 50));
    }

    @Test
    void limiteDaConsultaSubstituiOPadrao() {
        catalogo.buscar(ConsultaAoCatalogo.todas().comLimite(5));

        verify(buscas).buscar(new FiltroDeHabilidades(null, null, null, null, null, 5));
    }

    @Test
    void consultaNulaBuscaTodas() {
        catalogo.buscar(null);

        verify(buscas).buscar(new FiltroDeHabilidades(null, null, null, null, null, 50));
    }

    @Test
    void devolveAsHabilidadesNoTipoDaApi() {
        when(buscas.buscar(any())).thenReturn(
                List.of(new HabilidadeBncc("EF67LP08", "Texto.", "EF", List.of(6, 7), "Língua Portuguesa")));

        assertThat(catalogo.buscar(ConsultaAoCatalogo.todas()))
                .containsExactly(new HabilidadeDoCatalogo("EF67LP08", "Texto.", "EF", List.of(6, 7), "Língua Portuguesa"));
    }

    @Test
    void anoInvalidoNaConsultaFalha() {
        assertThatThrownBy(() -> catalogo.buscar(ConsultaAoCatalogo.todas().comAno(12)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, FiltroDeHabilidades.LIMITE_MAXIMO + 1})
    void limitePadraoForaDaFaixaImpedeASubida(int limite) {
        assertThatThrownBy(() -> new BuscaNoCatalogo(buscas, limite))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bncc.busca.limite-padrao");
    }
}
