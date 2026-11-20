package br.ufg.inf.labsimulacoes.cenarios;

import java.util.List;

/**
 * Os valores que os filtros da biblioteca podem ter agora, tirados das versões vigentes dos cenários
 * Aprovados (RF-12). A tela oferece só filtros que trazem algum cenário.
 *
 * @param componentes em ordem alfabética
 * @param anos em ordem crescente
 * @param habilidades códigos BNCC, em ordem
 */
public record FiltrosDaBiblioteca(List<String> componentes, List<Integer> anos, List<String> habilidades) {

    public FiltrosDaBiblioteca {
        componentes = List.copyOf(componentes);
        anos = List.copyOf(anos);
        habilidades = List.copyOf(habilidades);
    }
}
