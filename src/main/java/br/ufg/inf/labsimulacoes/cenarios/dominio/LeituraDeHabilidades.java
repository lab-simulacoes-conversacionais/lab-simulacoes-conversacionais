package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

/**
 * O que um leitor tirou do arquivo de uma etapa.
 *
 * @param etapa sigla da etapa lida, como EF
 * @param habilidades as habilidades vigentes
 * @param ignoradas quantas habilidades do arquivo não estão vigentes e ficaram de fora
 */
public record LeituraDeHabilidades(String etapa, List<HabilidadeBncc> habilidades, int ignoradas) {

    public LeituraDeHabilidades {
        habilidades = List.copyOf(habilidades);
    }
}
