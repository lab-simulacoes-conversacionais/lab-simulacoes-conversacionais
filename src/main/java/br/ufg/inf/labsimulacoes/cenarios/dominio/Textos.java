package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;
import java.util.Objects;

/** Limpeza dos textos de um cenário, que o rascunho aceita incompletos (RF-06). */
final class Textos {

    private Textos() {}

    /** Texto em branco vale como não informado, para contar como pendência no envio à curadoria. */
    static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    /** Um item em branco não pode contar como objetivo ou fonte (RN-02, RN-03). */
    static List<String> itens(List<String> valores) {
        if (valores == null) {
            return List.of();
        }
        return valores.stream().map(Textos::opcional).filter(Objects::nonNull).distinct().toList();
    }
}
