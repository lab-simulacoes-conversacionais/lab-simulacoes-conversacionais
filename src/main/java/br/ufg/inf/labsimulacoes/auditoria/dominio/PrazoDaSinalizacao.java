package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * O prazo do professor para sinalizar (RN-21): durante a sessão ou até 7 dias depois. O curador não tem
 * prazo, então esta regra não vale para ele.
 *
 * <p>Os 7 dias são 7 × 24 horas contadas do encerramento, com o limite incluído. Uma sessão que nunca foi
 * encerrada, como uma que ficou pausada, conta do início, para a janela não ficar aberta para sempre.
 */
public final class PrazoDaSinalizacao {

    public static final Duration JANELA = Duration.ofDays(7);

    private PrazoDaSinalizacao() {}

    /** De onde o prazo conta: o encerramento ou, se a sessão não foi encerrada, o início. */
    public static Instant base(Instant iniciadaEm, Instant encerradaEm) {
        Objects.requireNonNull(iniciadaEm, "iniciadaEm");
        return encerradaEm != null ? encerradaEm : iniciadaEm;
    }

    /** O último momento em que o professor ainda pode sinalizar. */
    public static Instant limite(Instant base) {
        return Objects.requireNonNull(base, "base").plus(JANELA);
    }

    /** Se o professor ainda pode sinalizar agora; o próprio limite ainda vale. */
    public static boolean permite(Instant base, Instant agora) {
        Objects.requireNonNull(agora, "agora");
        return !agora.isAfter(limite(base));
    }
}
