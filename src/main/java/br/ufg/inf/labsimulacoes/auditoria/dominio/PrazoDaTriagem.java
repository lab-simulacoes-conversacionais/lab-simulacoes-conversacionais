package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * O prazo do curador para triar uma sinalização (RN-24): até {@value #DIAS_UTEIS} dias úteis depois de ela
 * ser criada. Pendências fora do prazo ficam em destaque para o curador e o administrador.
 *
 * <p>Os dias úteis vão de segunda a sexta, no fuso da escola, e o prazo vence no mesmo horário em que a
 * sinalização foi criada, com o limite incluído: a de sexta às 15h vence na terça às 15h. A criada no fim
 * de semana conta a partir do início da segunda: a de sábado vence na quarta às 0h, no fim da terça, depois
 * de segunda e terça inteiras. A POC não tem calendário de feriados, então um feriado conta como dia útil.
 */
public final class PrazoDaTriagem {

    public static final int DIAS_UTEIS = 2;

    private PrazoDaTriagem() {}

    /** O último momento em que a triagem ainda está no prazo. */
    public static Instant limite(Instant criadaEm, ZoneId fuso) {
        Objects.requireNonNull(criadaEm, "criadaEm");
        Objects.requireNonNull(fuso, "fuso");
        ZonedDateTime momento = criadaEm.atZone(fuso);
        if (fimDeSemana(momento)) {
            momento = proximoDiaUtil(momento).toLocalDate().atStartOfDay(fuso);
        }
        for (int dia = 0; dia < DIAS_UTEIS; dia++) {
            momento = proximoDiaUtil(momento);
        }
        return momento.toInstant();
    }

    /** Se a sinalização ainda pendente passou do prazo agora; no próprio limite ainda está no prazo. */
    public static boolean foraDoPrazo(Instant criadaEm, Instant agora, ZoneId fuso) {
        Objects.requireNonNull(agora, "agora");
        return agora.isAfter(limite(criadaEm, fuso));
    }

    private static ZonedDateTime proximoDiaUtil(ZonedDateTime momento) {
        ZonedDateTime proximo = momento.plusDays(1);
        while (fimDeSemana(proximo)) {
            proximo = proximo.plusDays(1);
        }
        return proximo;
    }

    private static boolean fimDeSemana(ZonedDateTime momento) {
        DayOfWeek dia = momento.getDayOfWeek();
        return dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
    }
}
