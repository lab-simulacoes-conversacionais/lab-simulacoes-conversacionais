package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Habilidade do catálogo da BNCC (RF-05), identificada pelo código oficial, como EF08HI03.
 *
 * <p>É serializável porque o resultado das buscas fica em cache no Redis.
 *
 * @param codigo código oficial: etapa, ano ou bloco de anos, componente e número (EF08HI03, EF67LP08)
 * @param etapa sigla da etapa, que também abre o código (EF)
 * @param anos anos em que a habilidade vale, em ordem crescente e sem repetição
 * @param componente nome do componente curricular, como História
 */
public record HabilidadeBncc(String codigo, String descricao, String etapa, List<Integer> anos, String componente)
        implements Serializable {

    /** Vale para as etapas da BNCC: EF08HI03, EF15LP01, EI03EO01, EM13LGG101. */
    private static final Pattern CODIGO = Pattern.compile("[A-Z]{2}\\d{2}[A-Z]{2,4}\\d{2,3}");

    static final Pattern ETAPA = Pattern.compile("[A-Z]{2}");

    static final int PRIMEIRO_ANO = 1;
    static final int ULTIMO_ANO = 9;

    public HabilidadeBncc {
        codigo = preenchido(codigo, "código");
        descricao = preenchido(descricao, "descrição");
        etapa = preenchido(etapa, "etapa");
        componente = preenchido(componente, "componente");
        if (!ETAPA.matcher(etapa).matches()) {
            throw new IllegalArgumentException("A etapa " + etapa + " não é uma sigla da BNCC, como EF");
        }
        if (!CODIGO.matcher(codigo).matches() || !codigo.startsWith(etapa)) {
            throw new IllegalArgumentException(
                    "O código " + codigo + " não está no formato oficial da BNCC para a etapa " + etapa);
        }
        anos = anosValidos(codigo, anos);
    }

    /** Como a coluna ano guarda os anos: em ordem e separados por vírgula, como "6,7". */
    public String anosComoTexto() {
        return anos.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Integer> anosValidos(String codigo, List<Integer> anos) {
        if (anos == null || anos.isEmpty()) {
            throw new IllegalArgumentException("A habilidade " + codigo + " precisa valer para pelo menos um ano");
        }
        if (anos.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("A habilidade " + codigo + " tem um ano vazio");
        }
        for (int ano : anos) {
            if (ano < PRIMEIRO_ANO || ano > ULTIMO_ANO) {
                throw new IllegalArgumentException(
                        "O ano " + ano + " da habilidade " + codigo + " precisa estar entre 1 e 9");
            }
        }
        return anos.stream().distinct().sorted().toList();
    }

    private static String preenchido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " da habilidade é obrigatório");
        }
        return valor.strip();
    }
}
