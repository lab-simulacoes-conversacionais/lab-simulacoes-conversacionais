package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.ComparacaoDeVersoes;
import br.ufg.inf.labsimulacoes.cenarios.dominio.DiferencaDoCampo;
import java.util.ArrayList;
import java.util.List;

/**
 * Duas versões lado a lado, prontas para a tela (RF-11): cada valor vem marcado se saiu da versão antiga
 * ou entrou na nova, para o destaque das diferenças. Os campos que mudaram vêm primeiro, separados dos
 * que ficaram iguais.
 *
 * @param antiga número da versão de número menor
 * @param nova número da versão de número maior
 * @param mudaram os campos com alguma diferença, na ordem do editor
 * @param iguais os campos sem diferença, na ordem do editor
 */
record ComparacaoExibida(int antiga, int nova, List<Campo> mudaram, List<Campo> iguais) {

    ComparacaoExibida {
        mudaram = List.copyOf(mudaram);
        iguais = List.copyOf(iguais);
    }

    static ComparacaoExibida de(ComparacaoDeVersoes comparacao) {
        var mudaram = new ArrayList<Campo>();
        var iguais = new ArrayList<Campo>();
        for (DiferencaDoCampo diferenca : comparacao.campos()) {
            (diferenca.mudou() ? mudaram : iguais).add(Campo.de(diferenca));
        }
        return new ComparacaoExibida(comparacao.antiga().numero(), comparacao.nova().numero(), mudaram, iguais);
    }

    /** As duas versões têm o mesmo conteúdo em todos os campos. */
    public boolean semDiferencas() {
        return mudaram.isEmpty();
    }

    /**
     * Um campo nas duas versões.
     *
     * @param antes os valores da versão antiga; os marcados saíram na nova
     * @param depois os valores da versão nova; os marcados não estavam na antiga
     * @param soMudouAOrdem os mesmos itens em outra ordem: nada fica marcado, mas a tela avisa
     */
    record Campo(String rotulo, List<Valor> antes, List<Valor> depois, boolean soMudouAOrdem) {

        Campo {
            antes = List.copyOf(antes);
            depois = List.copyOf(depois);
        }

        static Campo de(DiferencaDoCampo diferenca) {
            return new Campo(
                    diferenca.campo().rotulo(),
                    marcados(diferenca.antes(), diferenca.depois()),
                    marcados(diferenca.depois(), diferenca.antes()),
                    diferenca.soMudouAOrdem());
        }

        /**
         * Marca os valores de {@code lista} que não estão em {@code outra}, contando as repetições pela
         * mesma regra de {@link DiferencaDoCampo#removidos()}: de ["A", "A"] para ["A"], o segundo "A"
         * saiu.
         */
        private static List<Valor> marcados(List<String> lista, List<String> outra) {
            var restantes = new ArrayList<>(outra);
            return lista.stream().map(valor -> new Valor(valor, !restantes.remove(valor))).toList();
        }
    }

    /** Um valor do campo; {@code destacado} se ele é uma diferença entre as versões. */
    record Valor(String texto, boolean destacado) {}
}
