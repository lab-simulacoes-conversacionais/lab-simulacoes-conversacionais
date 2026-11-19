package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** RN-02: conferência dos códigos no catálogo, sem banco. */
class ConferenciaNoCatalogoTest {

    private final Habilidades catalogo = new CatalogoEmMemoria(Set.of("EF08HI01", "EF08HI03"));

    @Test
    void codigosDoCatalogoPassam() {
        assertThatCode(() -> catalogo.exigirNoCatalogo(List.of("EF08HI01", "EF08HI03"))).doesNotThrowAnyException();
    }

    @Test
    void nenhumCodigoNaoTemOQueConferir() {
        assertThatCode(() -> catalogo.exigirNoCatalogo(List.of())).doesNotThrowAnyException();
    }

    @Test
    void recusaDizendoSoOsCodigosDesconhecidosNaOrdemPedida() {
        assertThatThrownBy(() -> catalogo.exigirNoCatalogo(List.of("EF09XX02", "EF08HI03", "EF01XX99")))
                .isInstanceOf(HabilidadesForaDoCatalogo.class)
                .hasMessageEndingWith("EF09XX02, EF01XX99");
    }

    private record CatalogoEmMemoria(Set<String> codigos) implements Habilidades {

        @Override
        public ResultadoDaGravacao gravar(List<HabilidadeBncc> habilidades) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<HabilidadeBncc> buscar(FiltroDeHabilidades filtro) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<String> existentes(Collection<String> pedidos) {
            return pedidos.stream().filter(codigos::contains).collect(Collectors.toSet());
        }
    }
}
