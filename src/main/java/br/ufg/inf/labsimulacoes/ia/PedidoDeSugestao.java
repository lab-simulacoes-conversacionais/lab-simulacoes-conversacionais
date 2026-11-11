package br.ufg.inf.labsimulacoes.ia;

import java.util.List;

/**
 * O que o professor ou o curador informa para pedir uma sugestão de cenário (RF-07).
 *
 * @param etapa sigla da etapa, como EF
 * @param ano ano escolar, de 1 a 9
 * @param habilidades códigos BNCC
 */
public record PedidoDeSugestao(String tema, String componente, String etapa, Integer ano, List<String> habilidades) {

    public PedidoDeSugestao {
        tema = Textos.obrigatorio(tema, "tema");
        componente = Textos.opcional(componente);
        etapa = Textos.opcional(etapa);
        habilidades = Textos.itens(habilidades);
    }
}
