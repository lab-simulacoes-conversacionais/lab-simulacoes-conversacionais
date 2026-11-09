package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Filtros da busca no catálogo da BNCC. Todos são opcionais e se combinam; o vazio vira {@code null}.
 *
 * <p>Os valores ficam normalizados, para que duas buscas que só diferem em maiúsculas ou espaços nas
 * pontas sejam o mesmo filtro e caiam na mesma chave do cache. Código e etapa ficam em maiúsculas;
 * descrição e componente, em minúsculas, porque são comparados sem diferenciar maiúsculas.
 *
 * @param codigo código igual a este
 * @param descricao descrição que contém este trecho
 * @param etapa etapa igual a esta
 * @param ano habilidade que vale para este ano, inclusive em bloco de anos (7 acha "6,7")
 * @param componente nome do componente igual a este
 * @param limite número máximo de habilidades no resultado
 */
public record FiltroDeHabilidades(
        String codigo, String descricao, String etapa, Integer ano, String componente, int limite) {

    /** Uma busca não traz mais que isto, mesmo se pedir. */
    public static final int LIMITE_MAXIMO = 500;

    public FiltroDeHabilidades {
        codigo = normalizado(codigo, true);
        descricao = normalizado(descricao, false);
        etapa = normalizado(etapa, true);
        componente = normalizado(componente, false);
        if (ano != null && (ano < 1 || ano > 9)) {
            throw new IllegalArgumentException("O ano da busca precisa estar entre 1 e 9");
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new IllegalArgumentException(
                    "O limite de resultados precisa estar entre 1 e " + LIMITE_MAXIMO);
        }
    }

    /**
     * Chave do cache. Cada valor vem com o seu tamanho na frente, para que nenhum texto digitado na
     * descrição consiga imitar a chave de outra combinação de filtros.
     */
    public String chave() {
        return Stream.of(codigo, descricao, etapa, ano == null ? null : ano.toString(), componente,
                        Integer.toString(limite))
                .map(valor -> valor == null ? "_" : valor.length() + ":" + valor)
                .collect(Collectors.joining("|"));
    }

    private static String normalizado(String valor, boolean maiusculas) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String semEspacos = valor.strip();
        return maiusculas ? semEspacos.toUpperCase(Locale.ROOT) : semEspacos.toLowerCase(Locale.ROOT);
    }
}
