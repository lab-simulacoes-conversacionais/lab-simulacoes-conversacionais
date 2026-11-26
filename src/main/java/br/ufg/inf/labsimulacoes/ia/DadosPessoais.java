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
 * minúsculas, com qualquer traço ou sinal de menos como hífen, sem caracteres invisíveis e com os espaços
 * juntados. No e-mail, "arroba", "(at)" e
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
 *   <li>o endereço precisa de um número depois da vírgula ou de "nº" ("Rua das Flores, 123"), de um CEP,
 *       de quadra e lote ou de um complemento logo depois do logradouro ("Rua das Flores, apto 12").
 *       "Logradouro, número" conta mesmo num contexto histórico, mas um ano de 1500 a 2099 logo depois da
 *       vírgula, sem "nº", conta como data ("A rua principal, 1920 foi asfaltada"), assim como uma
 *       porcentagem;
 *   <li>no e-mail, o @ e o ponto valem colados ou com um espaço de cada lado, nunca de um lado só
 *       ("siga @santosdumont. ele voou").
 * </ul>
 *
 * <p>Todos os quantificadores são limitados e nenhum fica aninhado em outro, para o tempo de busca
 * crescer em linha com o tamanho do texto (sem ReDoS).
 */
public final class DadosPessoais {

    private static final Pattern ORDINAIS = Pattern.compile("[ºª]");
    /** Travessão, meia-risca e os outros traços, e o sinal de menos, que o NFKD não troca pelo hífen. */
    private static final Pattern TRACOS = Pattern.compile("[\\p{Pd}\\u2212]");
    private static final Pattern INVISIVEIS = Pattern.compile("[\\p{M}\\p{Cf}]+");
    private static final Pattern ESPACOS = Pattern.compile("[\\s\\p{Z}]+");

    /** Os separadores escritos por extenso levam junto os espaços em volta. */
    private static final Pattern ARROBA =
            Pattern.compile(" ?(?:\\(arroba\\)|\\[arroba\\]|\\(at\\)|\\[at\\]|\\barroba\\b) ?");
    private static final Pattern PONTO =
            Pattern.compile(" ?(?:\\(ponto\\)|\\[ponto\\]|\\(dot\\)|\\[dot\\]|\\bponto\\b) ?");

    /**
     * O @ e o ponto valem colados ou com um espaço de cada lado ("fulano @ exemplo . com"), nunca com o
     * espaço de um lado só: assim "siga @santosdumont. ele" não vira e-mail.
     */
    private static final Pattern EMAIL = Pattern.compile(
            "[a-z0-9._%+-]{1,64}(?:@| @ )[a-z0-9-]{1,63}(?:(?:\\.| \\. )[a-z0-9-]{1,63}){1,4}");

    private static final List<Pattern> TELEFONES = List.of(
            // Celular: 9 + 8 dígitos, com ou sem DDD e +55.
            Pattern.compile("(?<!\\d)(?:\\+?55[ .-]?)?(?:\\(?[1-9]{2}\\)?[ .-]?)?9[ .-]?\\d{4}[ .-]?\\d{4}(?!\\d)"),
            // Fixo com o DDD entre parênteses ou depois do +55.
            Pattern.compile("(?<!\\d)(?:\\+55[ .-]?\\(?[1-9]{2}\\)?|(?:\\+?55[ .-]?)?\\([1-9]{2}\\))"
                    + "[ .-]?[2-5]\\d{3}[ .-]?\\d{4}(?!\\d)"),
            // Dez ou onze dígitos seguidos, começando com um DDD.
            Pattern.compile("(?<!\\d)[1-9]{2}(?:9\\d{8}|[2-5]\\d{7})(?!\\d)"),
            // Fixo ou celular sem DDD, só com uma palavra de telefone por perto.
            Pattern.compile("\\b(?:tel|telefone|fone|celular|cel|whats\\w{0,5}|zap|ligar|ligue|liga)\\b"
                    + "\\D{0,20}(?:[2-5]\\d{3}|9\\d{4})[ .-]?\\d{4}(?!\\d)"));

    /** Com ou sem pontuação, e com espaço, ponto ou hífen entre os blocos. */
    private static final Pattern CPF =
            Pattern.compile("(?<!\\d)\\d{3}[ .-]?\\d{3}[ .-]?\\d{3}[ .-]?\\d{2}(?!\\d)");

    private static final String LOGRADOURO =
            "\\b(?:rua|r\\.|avenida|av\\.?|travessa|alameda|praca|estrada|rodovia|largo) ";
    /**
     * Até três títulos abreviados logo no começo do nome, com o ponto ("Rua Dr. Fulano", "Av. Pres.
     * Vargas"). Só no começo: no meio do nome, um "min." de minutos atravessaria o fim da frase.
     */
    private static final String TITULOS =
            "(?:(?:dra|dr|profa|prof|pres|cel|gen|eng|sta|sto|des|min|cap|mal|ten|av|r)\\. ?){0,3}";
    /** O nome do logradouro, sem ponto, para a busca não atravessar o fim da frase ("TV. Em 1950"). */
    private static final String NOME = "[\\p{L}\\d '-]";
    private static final String ROTULO = "(?:n[°.]|numero|num\\.) ?";
    /** O número da casa, que não é porcentagem nem parte de um decimal. */
    private static final String NUMERO = "\\d{1,5}(?![\\p{L}\\d%]|[.,]\\d)";
    /** Um ano de 1500 a 2099 logo depois da vírgula conta como data, não como número da casa. */
    private static final String SEM_ANO = "(?!(?:1[5-9]|20)\\d{2}(?!\\d))";

    private static final List<Pattern> ENDERECOS = List.of(
            // CEP com hífen, como 12345-678 ou 12.345-678.
            Pattern.compile("(?<!\\d)\\d{2}\\.?\\d{3}-\\d{3}(?!\\d)"),
            // CEP sem hífen, só depois da palavra.
            Pattern.compile("\\bcep\\b\\D{0,5}\\d{2}\\.?\\d{3}-?\\d{3}(?!\\d)"),
            // Logradouro, nome e número depois de "nº", ou depois da vírgula quando não é um ano.
            Pattern.compile(LOGRADOURO + TITULOS + NOME + "{1,60}?(?:, ?" + ROTULO + NUMERO + "|, ?" + SEM_ANO + NUMERO
                    + "| " + ROTULO + NUMERO + ")"),
            // Logradouro com nome e, a até uns 40 caracteres, o complemento: "Rua das Flores, apto 12".
            Pattern.compile(LOGRADOURO + TITULOS + NOME + "{1,40}?, ?(?:" + NOME + "{0,20}?, ?)?"
                    + "(?:apto|apartamento|ap|bloco|bl|casa)\\.? ?\\d{1,5}\\b"),
            // Quadra e lote, como em Goiânia e Brasília.
            Pattern.compile("\\b(?:qd|quadra)\\.? ?\\d{1,4}\\b.{0,30}?\\b(?:lt|lote)\\.? ?\\d{1,4}\\b"));

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
        if (algum(ENDERECOS, normalizado)) {
            return Optional.of(TipoDeDadoPessoal.ENDERECO);
        }
        return Optional.empty();
    }

    /** Sem acentos, em minúsculas, sem caracteres invisíveis e com um espaço só entre as palavras. */
    static String normalizar(String texto) {
        String semOrdinais = ORDINAIS.matcher(texto).replaceAll("°");
        String decomposto = Normalizer.normalize(semOrdinais, Normalizer.Form.NFKD);
        String comHifenSimples = TRACOS.matcher(decomposto).replaceAll("-");
        String semMarcas = INVISIVEIS.matcher(comHifenSimples).replaceAll("");
        return ESPACOS.matcher(semMarcas.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /** O texto com "arroba" e "ponto" escritos por extenso trocados pelos símbolos, sem os espaços em volta. */
    static String paraEmail(String normalizado) {
        String comArroba = ARROBA.matcher(normalizado).replaceAll("@");
        return PONTO.matcher(comArroba).replaceAll(".");
    }

    private static boolean algum(List<Pattern> padroes, String texto) {
        return padroes.stream().anyMatch(padrao -> padrao.matcher(texto).find());
    }
}
