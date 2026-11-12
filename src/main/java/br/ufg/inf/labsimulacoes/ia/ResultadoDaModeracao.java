package br.ufg.inf.labsimulacoes.ia;

import java.util.List;

/**
 * Resultado da moderação de um texto (RN-20).
 *
 * @param sinalizado se o provedor considerou o texto impróprio
 * @param categorias categorias sinalizadas, nos nomes do provedor; vazia quando o texto passou. Pode
 *     vir vazia também com o texto sinalizado, quando o provedor marca uma categoria que o adaptador não
 *     conhece. Quem decide é {@code sinalizado}, nunca a lista.
 */
public record ResultadoDaModeracao(boolean sinalizado, List<String> categorias) {

    public ResultadoDaModeracao {
        categorias = Textos.itens(categorias);
    }

    public static ResultadoDaModeracao aprovado() {
        return new ResultadoDaModeracao(false, List.of());
    }
}
