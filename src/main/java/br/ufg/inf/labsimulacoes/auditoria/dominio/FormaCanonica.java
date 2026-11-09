package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Texto único de um valor, para o hash do log. É JSON com as chaves em ordem alfabética, então o
 * mesmo conteúdo dá o mesmo texto, seja ele montado agora ou lido do banco, onde o JSONB reordena
 * as chaves.
 *
 * <p>Os detalhes só aceitam texto, inteiro, booleano, listas e mapas desses tipos. Um número
 * decimal, por exemplo, poderia voltar do banco escrito de outro jeito e quebrar o hash.
 */
final class FormaCanonica {

    private FormaCanonica() {}

    /** Cópia imutável dos detalhes, com coleções viradas listas para a ordem não mudar depois. */
    static Map<String, Object> normalizarDetalhes(Map<String, ?> detalhes) {
        if (detalhes == null) {
            return Map.of();
        }
        var copia = new LinkedHashMap<String, Object>();
        detalhes.forEach((chave, valor) -> copia.put(exigirChave(chave), normalizar(valor)));
        return Map.copyOf(copia);
    }

    static String de(Object valor) {
        var texto = new StringBuilder();
        escrever(texto, valor);
        return texto.toString();
    }

    private static Object normalizar(Object valor) {
        return switch (valor) {
            case null -> throw new IllegalArgumentException("Os detalhes do log não aceitam valor nulo");
            case String texto -> texto;
            case Integer inteiro -> inteiro.longValue();
            case Long inteiro -> inteiro;
            case Boolean booleano -> booleano;
            case Collection<?> colecao -> {
                var lista = new ArrayList<>(colecao.size());
                colecao.forEach(item -> lista.add(normalizar(item)));
                yield List.copyOf(lista);
            }
            case Map<?, ?> mapa -> {
                var copia = new LinkedHashMap<String, Object>();
                mapa.forEach((chave, item) -> copia.put(exigirChave(chave), normalizar(item)));
                yield Map.copyOf(copia);
            }
            default -> throw new IllegalArgumentException(
                    "Tipo não aceito nos detalhes do log: " + valor.getClass().getSimpleName());
        };
    }

    private static String exigirChave(Object chave) {
        if (!(chave instanceof String texto) || texto.isEmpty()) {
            throw new IllegalArgumentException("As chaves dos detalhes do log precisam ser texto");
        }
        return texto;
    }

    private static void escrever(StringBuilder saida, Object valor) {
        switch (valor) {
            case null -> saida.append("null");
            case String texto -> escreverTexto(saida, texto);
            case Number numero -> saida.append(numero.longValue());
            case Boolean booleano -> saida.append(booleano);
            case List<?> lista -> {
                saida.append('[');
                for (int i = 0; i < lista.size(); i++) {
                    if (i > 0) {
                        saida.append(',');
                    }
                    escrever(saida, lista.get(i));
                }
                saida.append(']');
            }
            case Map<?, ?> mapa -> {
                var ordenado = new TreeMap<String, Object>();
                mapa.forEach((chave, item) -> ordenado.put((String) chave, item));
                saida.append('{');
                boolean primeiro = true;
                for (var par : ordenado.entrySet()) {
                    if (!primeiro) {
                        saida.append(',');
                    }
                    primeiro = false;
                    escreverTexto(saida, par.getKey());
                    saida.append(':');
                    escrever(saida, par.getValue());
                }
                saida.append('}');
            }
            default -> throw new IllegalArgumentException("Tipo sem forma canônica: " + valor.getClass());
        }
    }

    private static void escreverTexto(StringBuilder saida, String texto) {
        saida.append('"');
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        saida.append("\\u%04x".formatted((int) c));
                    } else {
                        saida.append(c);
                    }
                }
            }
        }
        saida.append('"');
    }
}
