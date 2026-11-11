package br.ufg.inf.labsimulacoes.ia.dominio;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Troca os marcadores {@code {{nome}}} de um texto pelos valores, em uma passagem só: o texto dos
 * valores é copiado como veio e nunca é examinado de novo. Assim, um {@code {{...}}} dentro dos dados
 * do cenário não é preenchido.
 */
final class PreenchimentoDeTemplate {

    private static final String ABERTURA = "{{";
    private static final Pattern MARCADOR = Pattern.compile("\\{\\{([a-z_]+)}}");

    private PreenchimentoDeTemplate() {}

    /**
     * @param origem versão e seção do template, para as mensagens de erro
     * @throws IllegalStateException se um marcador não tiver valor ou estiver malformado
     */
    static String preencher(String origem, String texto, Map<String, String> valores) {
        StringBuilder resultado = new StringBuilder(texto.length());
        Matcher marcador = MARCADOR.matcher(texto);
        int posicao = 0;
        int inicio;
        while ((inicio = texto.indexOf(ABERTURA, posicao)) >= 0) {
            if (!marcador.region(inicio, texto.length()).lookingAt()) {
                throw new IllegalStateException("Marcador malformado em " + origem + ", perto de \""
                        + trecho(texto, inicio) + "\": use {{nome}}, com letras minúsculas e _");
            }
            String nome = marcador.group(1);
            String valor = valores.get(nome);
            if (valor == null) {
                throw new IllegalStateException("O marcador {{" + nome + "}} de " + origem + " não tem valor");
            }
            resultado.append(texto, posicao, inicio).append(valor);
            posicao = marcador.end();
        }
        return resultado.append(texto, posicao, texto.length()).toString();
    }

    private static String trecho(String texto, int inicio) {
        return texto.substring(inicio, Math.min(texto.length(), inicio + 30));
    }
}
