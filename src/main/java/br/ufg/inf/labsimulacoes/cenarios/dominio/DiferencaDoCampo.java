package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Um campo nas duas versões comparadas (RF-11), para a tela mostrar lado a lado e destacar o que mudou.
 *
 * @param antes valores na versão mais antiga da comparação, na ordem em que foram escritos
 * @param depois valores na versão mais nova
 * @param removidos o que estava antes e não está depois, na ordem de antes, contando repetições
 * @param incluidos o que está depois e não estava antes, na ordem de depois, contando repetições
 */
public record DiferencaDoCampo(
        CampoDoCenario campo, List<String> antes, List<String> depois, List<String> removidos, List<String> incluidos) {

    public DiferencaDoCampo {
        Objects.requireNonNull(campo, "campo");
        antes = List.copyOf(antes);
        depois = List.copyOf(depois);
        removidos = List.copyOf(removidos);
        incluidos = List.copyOf(incluidos);
    }

    static DiferencaDoCampo entre(CampoDoCenario campo, List<String> antes, List<String> depois) {
        return new DiferencaDoCampo(campo, antes, depois, semOsDe(antes, depois), semOsDe(depois, antes));
    }

    /**
     * Os itens de {@code lista} que sobram depois de tirar, um a um, os de {@code outra}. Conta as
     * repetições: de ["A", "A"] para ["A"], um "A" saiu.
     */
    private static List<String> semOsDe(List<String> lista, List<String> outra) {
        var restantes = new ArrayList<>(outra);
        var sobram = new ArrayList<String>();
        for (String valor : lista) {
            if (!restantes.remove(valor)) {
                sobram.add(valor);
            }
        }
        return sobram;
    }

    /** Mudou se os valores ou a ordem deles mudaram: a ordem dos objetivos também é conteúdo. */
    public boolean mudou() {
        return !antes.equals(depois);
    }

    /** Os mesmos itens, em outra ordem: nada entrou nem saiu, mas a tela precisa dizer que mudou. */
    public boolean soMudouAOrdem() {
        return mudou() && removidos.isEmpty() && incluidos.isEmpty();
    }
}
