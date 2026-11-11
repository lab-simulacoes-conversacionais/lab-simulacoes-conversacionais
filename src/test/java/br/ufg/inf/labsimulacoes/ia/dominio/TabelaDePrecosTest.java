package br.ufg.inf.labsimulacoes.ia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TabelaDePrecosTest {

    private static final TabelaDePrecos TERRA = new TabelaDePrecos(new BigDecimal("1.00"), new BigDecimal("5.00"));

    @Test
    void custoSomaEntradaESaidaPeloPrecoDeCada() {
        // 316 × 1,00 + 97 × 5,00 = 801 dólares por milhão
        assertThat(TERRA.custoDe(new UsoDoModelo(316, 97))).isEqualByComparingTo("0.000801");
    }

    @Test
    void custoFicaComAsSeisCasasDaColuna() {
        BigDecimal custo = TERRA.custoDe(new UsoDoModelo(1, 0));

        assertThat(custo.scale()).isEqualTo(TabelaDePrecos.CASAS_DO_CUSTO);
        assertThat(custo).isEqualByComparingTo("0.000001");
    }

    @Test
    void custoArredondaAMetadeParaCima() {
        var precoQuebrado = new TabelaDePrecos(new BigDecimal("0.10"), new BigDecimal("0.50"));

        // 5 × 0,10 = 0,5 dólar por milhão = 0,0000005, que arredonda para 0,000001
        assertThat(precoQuebrado.custoDe(new UsoDoModelo(5, 0))).isEqualByComparingTo("0.000001");
        assertThat(precoQuebrado.custoDe(new UsoDoModelo(4, 0))).isEqualByComparingTo("0");
    }

    @Test
    void semTokensInformadosOCustoFicaDesconhecido() {
        assertThat(TERRA.custoDe(UsoDoModelo.desconhecido())).isNull();
        assertThat(TERRA.custoDe(new UsoDoModelo(316, null))).isNull();
        assertThat(TERRA.custoDe(new UsoDoModelo(null, 97))).isNull();
    }

    @Test
    void zeroTokensCustaZero() {
        assertThat(TERRA.custoDe(new UsoDoModelo(0, 0))).isEqualByComparingTo("0");
    }

    @Test
    void precosSaoObrigatoriosENaoNegativos() {
        assertThatThrownBy(() -> new TabelaDePrecos(null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("de entrada");
        assertThatThrownBy(() -> new TabelaDePrecos(BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("de saída");
        assertThatThrownBy(() -> new TabelaDePrecos(new BigDecimal("-0.01"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
        assertThatThrownBy(() -> new TabelaDePrecos(BigDecimal.ONE, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    void precoZeroEhPermitido() {
        assertThat(new TabelaDePrecos(BigDecimal.ZERO, BigDecimal.ZERO).custoDe(new UsoDoModelo(10, 10)))
                .isEqualByComparingTo("0");
    }
}
