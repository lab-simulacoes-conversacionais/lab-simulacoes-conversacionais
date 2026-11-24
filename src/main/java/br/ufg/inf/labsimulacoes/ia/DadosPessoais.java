package br.ufg.inf.labsimulacoes.ia;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Procura padrões de dado pessoal num texto digitado, antes de ele ir ao provedor (RN-13, RNF-01): e-mail,
 * telefone, CPF e endereço, nos formatos usados no Brasil. Só Java, sem banco nem Spring.
 *
 * <p>O texto é normalizado só para a busca, e a forma normalizada não sai daqui: sem acentos, em
 * minúsculas, sem caracteres invisíveis e com os espaços juntados. No e-mail, "arroba", "(at)" e
 * "[at]" valem como @, e "ponto", "(dot)" e "[dot]", como ponto. Números por extenso não são
 * reconhecidos.
 *
 * <p>As regras evitam os falsos positivos comuns numa aula: códigos da BNCC, datas, anos e intervalos
 * de anos ("1914-1918"), notas, medidas e logradouros históricos sem número ("Rua 25 de Março").
 * Por isso:
 *
 * <ul>
 *   <li>o telefone fixo sem DDD só conta com uma palavra de telefone por perto ("tel", "whatsapp",
 *       "ligar"...);
 *   <li>o celular precisa dos nove dígitos começando com 9;
 *   <li>o CPF vale pelo formato, sem conferir os dígitos verificadores, porque um CPF digitado com erro
 *       continua sendo dado pessoal;
 *   <li>o endereço precisa de um número depois da vírgula ou de "nº" ("Rua das Flores, 123"), de um CEP
 *       ou de quadra e lote. "Logradouro, número" conta mesmo num contexto histórico.
 * </ul>
 *
 * <p>Todos os quantificadores são limitados e nenhum fica aninhado em outro, para o tempo de busca
 * crescer em linha com o tamanho do texto (sem ReDoS).
 */
public final class DadosPessoais {

    private static final Pattern ORDINAIS = Pattern.compile("[ºª]");
    private static final Pattern INVISIVEIS = Pattern.compile("[\\p{M}\\p{Cf}]+");
    private static final Pattern ESPACOS = Pattern.compile("[\\s\\p{Z}]+");

    private static final Pattern ARROBA = Pattern.compile(" ?(?:\\barroba\\b|\\(at\\)|\\[at\\]) ?");
    private static final Pattern PONTO =
            Pattern.compile(" ?(?:\\bponto\\b|\\(dot\\)|\\[dot\\]|\\(ponto\\)|\\[ponto\\]) ?");
    private static final Pattern SEPARADOR_DO_EMAIL = Pattern.compile(" ?([@.]) ?");

    private static final Pattern EMAIL =
            Pattern.compile("[a-z0-9._%+-]{1,64}@[a-z0-9-]{1,63}(?:\\.[a-z0-9-]{1,63}){1,4}");

    private static final List<Pattern> TELEFONES = List.of(
            // Celular: 9 + 8 dígitos, com ou sem DDD e +55.
            Pattern.compile("(?<!\\d)(?:\\+?55[ .-]?)?(?:\\(?[1-9]{2}\\)?[ .-]?)?9[ .]?\\d{4}[ .-]?\\d{4}(?!\\d)"),
            // Fixo com o DDD entre parênteses ou depois do +55.
            Pattern.compile("(?<!\\d)(?:\\+55[ .-]?\\(?[1-9]{2}\\)?|(?:\\+?55[ .-]?)?\\([1-9]{2}\\))"
                    + "[ .-]?[2-5]\\d{3}[ .-]?\\d{4}(?!\\d)"),
            // Dez ou onze dígitos seguidos, começando com um DDD.
            Pattern.compile("(?<!\\d)[1-9]{2}(?:9\\d{8}|[2-5]\\d{7})(?!\\d)"),
            // Fixo ou celular sem DDD, só com uma palavra de telefone por perto.
            Pattern.compile("\\b(?:tel|telefone|fone|celular|cel|whats\\w{0,5}|zap|ligar|ligue|liga)\\b"
                    + "\\D{0,20}(?:[2-5]\\d{3}|9\\d{4})[ .-]?\\d{4}(?!\\d)"));

    private static final Pattern CPF = Pattern.compile("(?<!\\d)\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?!\\d)");

    private static final Pattern LOGRADOURO_NA_FRASE =
            Pattern.compile("\\b(?:rua|avenida|av|travessa|alameda|praca|estrada|rodovia|condominio|residencial"
                    + "|edificio)\\b");

    private static final List<Pattern> ENDERECOS = List.of(
            // CEP com hífen, como 12345-678 ou 12.345-678.
            Pattern.compile("(?<!\\d)\\d{2}\\.?\\d{3}-\\d{3}(?!\\d)"),
            // CEP sem hífen, só depois da palavra.
            Pattern.compile("\\bcep\\b\\D{0,5}\\d{2}\\.?\\d{3}-?\\d{3}(?!\\d)"),
            // Logradouro, nome e número depois da vírgula ou de "nº".
            Pattern.compile("\\b(?:rua|r\\.|avenida|av\\.?|travessa|tv\\.|alameda|praca|estrada|rodovia|largo) "
                    + "[\\p{L}\\d .'-]{1,60}?(?:, ?(?:(?:n[°.]|numero|num\\.) ?)?| (?:n[°.]|numero|num\\.) ?)"
                    + "\\d{1,5}(?![\\p{L}\\d])"),
            // Quadra e lote, como em Goiânia e Brasília.
            Pattern.compile("\\b(?:qd|quadra)\\.? ?\\d{1,4}\\b.{0,30}?\\b(?:lt|lote)\\.? ?\\d{1,4}\\b"));

    private static final Pattern COMPLEMENTO = Pattern.compile("\\b(?:apto|apartamento|ap|bloco|bl|casa)\\.? ?\\d{1,5}\\b");

    private DadosPessoais() {}

    /**
     * O primeiro tipo de dado pessoal encontrado, na ordem e-mail, telefone, CPF e endereço.
     *
     * @return vazio se o texto for nulo, vazio ou não tiver nenhum padrão
     */
    public static Optional<TipoDeDadoPessoal> procurar(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String normalizado = normalizar(texto);
        if (EMAIL.matcher(paraEmail(normalizado)).find()) {
            return Optional.of(TipoDeDadoPessoal.EMAIL);
        }
        if (algum(TELEFONES, normalizado)) {
            return Optional.of(TipoDeDadoPessoal.TELEFONE);
        }
        if (CPF.matcher(normalizado).find()) {
            return Optional.of(TipoDeDadoPessoal.CPF);
        }
        if (algum(ENDERECOS, normalizado)
                || (COMPLEMENTO.matcher(normalizado).find() && LOGRADOURO_NA_FRASE.matcher(normalizado).find())) {
            return Optional.of(TipoDeDadoPessoal.ENDERECO);
        }
        return Optional.empty();
    }

    /** Sem acentos, em minúsculas, sem caracteres invisíveis e com um espaço só entre as palavras. */
    static String normalizar(String texto) {
        String semOrdinais = ORDINAIS.matcher(texto).replaceAll("°");
        String decomposto = Normalizer.normalize(semOrdinais, Normalizer.Form.NFKD);
        String semMarcas = INVISIVEIS.matcher(decomposto).replaceAll("");
        return ESPACOS.matcher(semMarcas.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /** O texto com "arroba" e "ponto" escritos por extenso e sem espaços em volta do @ e do ponto. */
    static String paraEmail(String normalizado) {
        String comArroba = ARROBA.matcher(normalizado).replaceAll("@");
        String comPonto = PONTO.matcher(comArroba).replaceAll(".");
        return SEPARADOR_DO_EMAIL.matcher(comPonto).replaceAll("$1");
    }

    private static boolean algum(List<Pattern> padroes, String texto) {
        return padroes.stream().anyMatch(padrao -> padrao.matcher(texto).find());
    }
}
