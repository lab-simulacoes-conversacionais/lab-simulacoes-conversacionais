package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

/**
 * Os valores que os filtros da biblioteca podem ter, tirados dos próprios cenários Aprovados (RF-12).
 * Assim a tela só oferece filtros que trazem algum cenário.
 *
 * @param componentes em ordem alfabética
 * @param anos em ordem crescente
 * @param habilidades códigos BNCC, em ordem
 */
public record OpcoesDaBiblioteca(List<String> componentes, List<Integer> anos, List<String> habilidades) {

    public OpcoesDaBiblioteca {
        componentes = List.copyOf(componentes);
        anos = List.copyOf(anos);
        habilidades = List.copyOf(habilidades);
    }
}
