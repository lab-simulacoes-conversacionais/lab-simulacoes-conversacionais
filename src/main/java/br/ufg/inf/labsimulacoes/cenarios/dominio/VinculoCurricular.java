package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;
import java.util.Locale;

/**
 * Onde o cenário se encaixa na BNCC (RN-02). Fica em colunas e tabelas relacionais, porque serve para
 * filtros e integridade (Stack, seção 6).
 *
 * <p>Etapa e ano usam o formato do catálogo, para que os filtros da biblioteca casem com ele. O que
 * vier informado precisa ser válido já no rascunho.
 *
 * @param etapa sigla da etapa, como EF
 * @param ano ano escolar, de 1 a 9
 * @param habilidades códigos oficiais, em ordem e sem repetição
 */
public record VinculoCurricular(String componente, String etapa, Integer ano, List<String> habilidades) {

    public VinculoCurricular {
        componente = Textos.opcional(componente);
        etapa = etapaValida(etapa);
        if (ano != null && (ano < HabilidadeBncc.PRIMEIRO_ANO || ano > HabilidadeBncc.ULTIMO_ANO)) {
            throw new IllegalArgumentException("O ano " + ano + " precisa estar entre 1 e 9");
        }
        habilidades = codigos(habilidades);
    }

    static VinculoCurricular vazio() {
        return new VinculoCurricular(null, null, null, null);
    }

    private static String etapaValida(String etapa) {
        String sigla = Textos.opcional(etapa);
        if (sigla == null) {
            return null;
        }
        String maiuscula = sigla.toUpperCase(Locale.ROOT);
        if (!HabilidadeBncc.ETAPA.matcher(maiuscula).matches()) {
            throw new IllegalArgumentException("A etapa " + sigla + " não é uma sigla da BNCC, como EF");
        }
        return maiuscula;
    }

    private static List<String> codigos(List<String> habilidades) {
        return Textos.itens(habilidades).stream()
                .map(codigo -> codigo.toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }
}
