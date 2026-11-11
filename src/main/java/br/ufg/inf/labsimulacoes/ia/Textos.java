package br.ufg.inf.labsimulacoes.ia;

import java.util.List;
import java.util.Objects;

/** Normalização dos textos que chegam pela porta do adaptador. */
final class Textos {

    private Textos() {}

    static String opcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    static String obrigatorio(String texto, String campo) {
        String valor = opcional(texto);
        if (valor == null) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório");
        }
        return valor;
    }

    /** Sem itens vazios e sem nulos; uma lista ausente vira vazia. */
    static List<String> itens(List<String> itens) {
        return itens == null
                ? List.of()
                : itens.stream().filter(Objects::nonNull).map(Textos::opcional).filter(Objects::nonNull).toList();
    }
}
