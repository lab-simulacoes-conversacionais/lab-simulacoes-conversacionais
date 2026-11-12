package br.ufg.inf.labsimulacoes.sessoes.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Junta os trechos da resposta em frases, para cada frase ser moderada antes de ir para a tela (RN-20,
 * Stack, seção 5, passo 7). Não guarda estado entre respostas: cada resposta usa um agrupador novo.
 *
 * <p>Uma frase termina em ponto, exclamação, interrogação ou reticências (com aspas ou parênteses de
 * fechamento logo depois) seguidos de espaço, ou numa quebra de linha. O espaço logo depois do fim
 * fica com a frase, e não com a seguinte, porque o SSE descarta o espaço do início dos dados. Juntar as
 * frases devolve o texto original.
 *
 * <ul>
 *   <li>O ponto de uma abreviação conhecida ("Sr.", "Dra.", "séc.") ou de uma inicial ("D. Pedro") não
 *       fecha a frase, nem o de uma palavra que já tem ponto ("a.C.").
 *   <li>O ponto depois de um número fecha a frase ("em 1906. Depois"), mas o ponto entre dígitos não
 *       ("3.5", "1.000"), porque não é seguido de espaço.
 *   <li>Enquanto o fim do texto é um ponto, não dá para saber se a frase acabou: ela espera o próximo
 *       trecho ou {@link #terminar()}.
 *   <li>Um texto sem pontuação é cortado no último espaço antes de {@value #LIMITE_SEM_PONTUACAO}
 *       caracteres, para a tela não ficar parada esperando um fim que não vem.
 * </ul>
 */
public final class AgrupadorDeFrases {

    /** Bem acima de uma frase comum, e abaixo das cerca de 120 palavras de uma resposta (RN-16). */
    public static final int LIMITE_SEM_PONTUACAO = 300;

    private static final String FINAIS = ".!?…";
    private static final String FECHAMENTOS = "\"')]»”’";
    private static final Set<String> ABREVIACOES = Set.of(
            "sr", "sra", "srta", "srs", "sras", "dr", "dra", "drs", "dras", "prof", "profa", "profs", "profas",
            "sto", "sta", "exmo", "exma", "av", "séc", "sec", "sécs", "pág", "págs", "pp", "aprox", "obs", "ex",
            "vol", "cap", "fig", "nº", "gal", "gen", "cel", "maj", "ten", "sgt", "pe", "fr");

    private final StringBuilder pendente = new StringBuilder();

    /** @return as frases que o trecho completou, na ordem; o resto espera o próximo trecho */
    public List<String> acrescentar(String trecho) {
        if (trecho != null) {
            pendente.append(trecho);
        }
        List<String> frases = new ArrayList<>();
        int fim;
        while ((fim = fimDaPrimeiraFrase()) > 0) {
            frases.add(pendente.substring(0, fim));
            pendente.delete(0, fim);
        }
        return frases;
    }

    /** O que sobrou quando a resposta terminou, se não for só espaço. */
    public Optional<String> terminar() {
        String resto = pendente.toString();
        pendente.setLength(0);
        return resto.isBlank() ? Optional.empty() : Optional.of(resto);
    }

    /** Posição logo depois do fim da primeira frase, ou -1 se ela ainda não acabou. */
    private int fimDaPrimeiraFrase() {
        int tamanho = pendente.length();
        boolean temTexto = false;
        for (int i = 0; i < tamanho; i++) {
            char c = pendente.charAt(i);
            if (c == '\n') {
                if (temTexto) {
                    return i + 1;
                }
                continue;
            }
            if (!Character.isWhitespace(c)) {
                temTexto = true;
            }
            if (FINAIS.indexOf(c) < 0) {
                continue;
            }
            int depois = i + 1;
            while (depois < tamanho && FINAIS.indexOf(pendente.charAt(depois)) >= 0) {
                depois++;
            }
            boolean pontoSozinho = c == '.' && depois == i + 1;
            while (depois < tamanho && FECHAMENTOS.indexOf(pendente.charAt(depois)) >= 0) {
                depois++;
            }
            if (depois == tamanho) {
                return -1;
            }
            if (Character.isWhitespace(pendente.charAt(depois)) && !(pontoSozinho && abreviacaoAntes(i))) {
                return depoisDosEspacos(depois);
            }
            i = depois - 1;
        }
        return tamanho >= LIMITE_SEM_PONTUACAO ? corteSemPontuacao() : -1;
    }

    private int corteSemPontuacao() {
        for (int i = LIMITE_SEM_PONTUACAO - 1; i > 0; i--) {
            if (Character.isWhitespace(pendente.charAt(i))) {
                return depoisDosEspacos(i);
            }
        }
        return LIMITE_SEM_PONTUACAO;
    }

    /** Os espaços já recebidos depois do fim ficam com a frase; uma quebra de linha também. */
    private int depoisDosEspacos(int inicio) {
        int fim = inicio;
        while (fim < pendente.length() && Character.isWhitespace(pendente.charAt(fim))) {
            fim++;
        }
        return fim;
    }

    /** A palavra que termina no ponto da posição dada é uma abreviação ou uma inicial. */
    private boolean abreviacaoAntes(int ponto) {
        int inicio = ponto;
        while (inicio > 0 && !Character.isWhitespace(pendente.charAt(inicio - 1)) && pendente.charAt(inicio - 1) != '(') {
            inicio--;
        }
        String palavra = pendente.substring(inicio, ponto).toLowerCase(Locale.ROOT);
        if (palavra.isEmpty()) {
            return false;
        }
        if (palavra.length() == 1 && Character.isLetter(palavra.charAt(0))) {
            return true;
        }
        return palavra.indexOf('.') >= 0 || ABREVIACOES.contains(palavra);
    }
}
