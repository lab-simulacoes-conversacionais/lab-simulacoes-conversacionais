package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * As datas da fila de triagem e do log como o protótipo mostra, no fuso da escola: "14 jun" e
 * "14 jun 2026 · 10:42". O mês abreviado vem sem o ponto que o Java põe em português ("jun.").
 */
final class DatasNaTela {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private static final Locale PORTUGUES = Locale.of("pt", "BR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("d MMM", PORTUGUES);
    private static final DateTimeFormatter ANO = DateTimeFormatter.ofPattern("yyyy", PORTUGUES);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", PORTUGUES);

    private DatasNaTela() {}

    /** "14 jun". */
    static String dia(Instant instante) {
        return dia(instante.atZone(FUSO).toLocalDate());
    }

    /** "14 jun · 10:42". */
    static String diaEHora(Instant instante) {
        ZonedDateTime momento = instante.atZone(FUSO);
        return dia(momento.toLocalDate()) + " · " + HORA.format(momento);
    }

    /** "14 jun 2026 · 10:42", para o log, que guarda as ações para sempre. */
    static String completo(Instant instante) {
        ZonedDateTime momento = instante.atZone(FUSO);
        return dia(momento.toLocalDate()) + " " + ANO.format(momento) + " · " + HORA.format(momento);
    }

    /**
     * O limite da triagem (RN-24). O prazo de uma sinalização do fim de semana vence à 0h, que na tela seria
     * lido como o começo do dia; por isso mostra o dia anterior, até o fim dele.
     */
    static String limite(Instant limite) {
        ZonedDateTime momento = limite.atZone(FUSO);
        if (momento.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            return dia(momento.toLocalDate().minusDays(1)) + " · fim do dia";
        }
        return dia(momento.toLocalDate()) + " · " + HORA.format(momento);
    }

    /** O início do dia no fuso da escola, para os filtros de período. */
    static Instant inicioDo(LocalDate dia) {
        return dia.atStartOfDay(FUSO).toInstant();
    }

    private static String dia(LocalDate data) {
        return DIA.format(data).replace(".", "");
    }
}
