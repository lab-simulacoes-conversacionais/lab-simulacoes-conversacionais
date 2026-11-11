package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaginaTest {

    @Test
    void deslocamentoPulaAsPaginasAnteriores() {
        assertThat(new Pagina(0, 20).deslocamento()).isZero();
        assertThat(new Pagina(3, 20).deslocamento()).isEqualTo(60);
    }

    @Test
    void deslocamentoNaoEstouraOInt() {
        assertThat(new Pagina(Integer.MAX_VALUE, 100).deslocamento()).isEqualTo(Integer.MAX_VALUE * 100L);
    }

    @Test
    void tamanhoMaximoEAceito() {
        assertThat(new Pagina(0, Pagina.TAMANHO_MAXIMO).tamanho()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Pagina.TAMANHO_MAXIMO + 1})
    void tamanhoForaDaFaixaERecusado(int tamanho) {
        assertThatThrownBy(() -> new Pagina(0, tamanho))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 e 100");
    }

    @Test
    void paginaNegativaERecusada() {
        assertThatThrownBy(() -> new Pagina(-1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("começa em 0");
    }

    @Test
    void trechoNaoMudaSeAListaOriginalMudar() {
        var itens = new ArrayList<CenarioNaBiblioteca>();
        var trecho = new TrechoDaBiblioteca(itens, false);

        itens.add(new CenarioNaBiblioteca(1L, new VersaoDoCenario(
                2L, 1, StatusDaVersao.APROVADA, ConteudosDeTeste.completo(), 7L)));

        assertThat(trecho.itens()).isEmpty();
        assertThat(List.copyOf(itens)).hasSize(1);
    }

    @Test
    void cenarioDaBibliotecaPrecisaDaVersao() {
        assertThatThrownBy(() -> new CenarioNaBiblioteca(1L, null)).isInstanceOf(NullPointerException.class);
    }
}
