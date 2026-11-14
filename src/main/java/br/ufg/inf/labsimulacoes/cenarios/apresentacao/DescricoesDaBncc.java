package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.cenarios.CatalogoBncc;
import br.ufg.inf.labsimulacoes.cenarios.ConsultaAoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.HabilidadeDoCatalogo;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * A descrição de cada código BNCC de um cenário, para quem edita, revisa ou escolhe o cenário conferir
 * o que foi vinculado (RN-02). A busca por código passa pelo cache do catálogo.
 */
@Component
class DescricoesDaBncc {

    private final CatalogoBncc catalogo;

    DescricoesDaBncc(CatalogoBncc catalogo) {
        this.catalogo = catalogo;
    }

    /** Na ordem dos códigos. Um código que saiu do catálogo aparece com um aviso, em vez de sumir. */
    List<HabilidadeDoCatalogo> de(List<String> codigos) {
        return codigos.stream()
                .map(codigo -> catalogo.buscar(ConsultaAoCatalogo.porCodigo(codigo)).stream()
                        .findFirst()
                        .orElseGet(() -> new HabilidadeDoCatalogo(
                                codigo.toUpperCase(Locale.ROOT),
                                "Código fora do catálogo da BNCC. Remova ou corrija.",
                                FormularioDoCenario.ETAPA,
                                List.of(),
                                "")))
                .toList();
    }
}
