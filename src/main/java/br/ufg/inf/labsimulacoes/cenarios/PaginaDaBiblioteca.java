package br.ufg.inf.labsimulacoes.cenarios;

import java.util.List;

/**
 * Uma página da {@link BibliotecaDeCenarios}.
 *
 * @param cenarios os cenários da página, na ordem da biblioteca
 * @param pagina o número desta página, a partir de 0
 * @param tamanho o tamanho pedido
 * @param temProxima se há cenários na página seguinte
 */
public record PaginaDaBiblioteca(List<CenarioDaBiblioteca> cenarios, int pagina, int tamanho, boolean temProxima) {

    public PaginaDaBiblioteca {
        cenarios = List.copyOf(cenarios);
    }
}
