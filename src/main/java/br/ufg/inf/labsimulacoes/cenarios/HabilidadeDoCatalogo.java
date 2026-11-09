package br.ufg.inf.labsimulacoes.cenarios;

import java.util.List;

/**
 * Habilidade da BNCC encontrada no catálogo.
 *
 * @param codigo código oficial, como EF08HI03
 * @param etapa sigla da etapa, como EF
 * @param anos anos em que a habilidade vale, em ordem crescente ([6, 7] para EF67LP08)
 * @param componente nome do componente curricular, como História
 */
public record HabilidadeDoCatalogo(
        String codigo, String descricao, String etapa, List<Integer> anos, String componente) {

    public HabilidadeDoCatalogo {
        anos = List.copyOf(anos);
    }
}
