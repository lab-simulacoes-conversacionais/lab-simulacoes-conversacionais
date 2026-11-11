package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

/**
 * Uma página da biblioteca.
 *
 * @param itens os cenários da página, na ordem da biblioteca
 * @param temProxima se há cenários depois desta página
 */
public record TrechoDaBiblioteca(List<CenarioNaBiblioteca> itens, boolean temProxima) {

    public TrechoDaBiblioteca {
        itens = List.copyOf(itens);
    }
}
