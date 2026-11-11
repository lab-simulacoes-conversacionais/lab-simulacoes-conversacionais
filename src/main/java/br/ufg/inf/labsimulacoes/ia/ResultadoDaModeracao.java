package br.ufg.inf.labsimulacoes.ia;

import java.util.List;

/**
 * Resultado da moderação de um texto (RN-20).
 *
 * @param sinalizado se o provedor considerou o texto impróprio
 * @param categorias categorias sinalizadas, nos nomes do provedor; vazia quando o texto passou
 */
public record ResultadoDaModeracao(boolean sinalizado, List<String> categorias) {

    public ResultadoDaModeracao {
        categorias = Textos.itens(categorias);
    }

    public static ResultadoDaModeracao aprovado() {
        return new ResultadoDaModeracao(false, List.of());
    }
}
