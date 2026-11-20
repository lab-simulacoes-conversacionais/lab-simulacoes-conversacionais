package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Busca e filtros da biblioteca (RF-12). Todos são opcionais e se combinam; o vazio vira {@code null}
 * e não filtra nada.
 *
 * <p>O texto fica em minúsculas e sem acentos, porque a busca não diferencia "Revolução" de
 * "revolucao". O componente também é comparado sem acento, mas guarda o valor como veio. A habilidade
 * fica em maiúsculas, como o código oficial.
 *
 * @param texto trecho procurado no título, no componente, no personagem, no contexto ou nos objetivos,
 *     que é onde aparece o tema do cenário
 * @param componente componente curricular igual a este, sem diferenciar maiúsculas nem acentos
 * @param ano ano escolar, de 1 a 9
 * @param habilidade código BNCC vinculado à versão vigente, como EF08HI03
 */
public record BuscaNaBiblioteca(String texto, String componente, Integer ano, String habilidade) {

    /** Sem filtro: a biblioteca inteira. */
    public static final BuscaNaBiblioteca TUDO = new BuscaNaBiblioteca(null, null, null, null);

    /** Uma busca maior que isto não é um tema, e só deixaria a consulta mais cara. */
    public static final int TAMANHO_MAXIMO_DO_TEXTO = 100;

    /**
     * Letras acentuadas e as mesmas sem acento, na mesma posição. É a única regra de acento da busca: o
     * Java usa a lista em {@link #semAcento} e o banco usa a mesma lista no {@code translate()}, porque o
     * PostgreSQL não tem a extensão unaccent. Letra fora da lista fica como está nos dois lados.
     */
    public static final String COM_ACENTO = "áàâãäåéèêëíìîïóòôõöøúùûüçñýÿ";

    public static final String SEM_ACENTO = "aaaaaaeeeeiiiioooooouuuucnyy";

    public BuscaNaBiblioteca {
        texto = semAcento(Textos.opcional(texto));
        if (texto != null && texto.length() > TAMANHO_MAXIMO_DO_TEXTO) {
            throw new IllegalArgumentException(
                    "A busca na biblioteca aceita até " + TAMANHO_MAXIMO_DO_TEXTO + " caracteres");
        }
        componente = Textos.opcional(componente);
        if (ano != null && (ano < HabilidadeBncc.PRIMEIRO_ANO || ano > HabilidadeBncc.ULTIMO_ANO)) {
            throw new IllegalArgumentException("O ano " + ano + " precisa estar entre 1 e 9");
        }
        habilidade = Textos.opcional(habilidade);
        habilidade = habilidade == null ? null : habilidade.toUpperCase(Locale.ROOT);
    }

    /** O componente na forma em que é comparado: minúsculas e sem acento. */
    public String componenteSemAcento() {
        return semAcento(componente);
    }

    /**
     * Minúsculas sem acento, pela mesma regra do banco: junta o acento que veio separado da letra (NFC),
     * passa para minúsculas e troca as letras de {@link #COM_ACENTO} pelas de {@link #SEM_ACENTO}.
     */
    public static String semAcento(String valor) {
        if (valor == null) {
            return null;
        }
        String minusculas = Normalizer.normalize(valor, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        var resultado = new StringBuilder(minusculas.length());
        for (char letra : minusculas.toCharArray()) {
            int posicao = COM_ACENTO.indexOf(letra);
            resultado.append(posicao < 0 ? letra : SEM_ACENTO.charAt(posicao));
        }
        return resultado.toString();
    }
}
