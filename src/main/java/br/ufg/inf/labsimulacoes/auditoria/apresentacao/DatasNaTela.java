package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * As datas da fila de triagem e do log como o protótipo mostra, no fuso da escola: "14 jun" e
 * "14 jun 2026 · 10:42". O mês abreviado vem sem o ponto que o Java põe em português ("jun.").
 *
 * <p>O fuso é o mesmo com que a Auditoria calcula o prazo da triagem ({@code auditoria.triagem.fuso}), para a
 * tela e o domínio concordarem sobre o dia da escola: o limite à meia-noite e os dias do filtro do log.
 */
@Component
class DatasNaTela {

    private static final Locale PORTUGUES = Locale.of("pt", "BR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("d MMM", PORTUGUES);
    private static final DateTimeFormatter ANO = DateTimeFormatter.ofPattern("yyyy", PORTUGUES);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", PORTUGUES);

    private final ZoneId fuso;

    DatasNaTela(@Value("${auditoria.triagem.fuso}") ZoneId fuso) {
        this.fuso = fuso;
    }

    /** "14 jun". */
    String dia(Instant instante) {
        return dia(instante.atZone(fuso).toLocalDate());
    }

    /** "14 jun · 10:42". */
    String diaEHora(Instant instante) {
        ZonedDateTime momento = instante.atZone(fuso);
        return dia(momento.toLocalDate()) + " · " + HORA.format(momento);
    }

    /** "14 jun 2026 · 10:42", para o log, que guarda as ações para sempre. */
    String completo(Instant instante) {
        ZonedDateTime momento = instante.atZone(fuso);
        return dia(momento.toLocalDate()) + " " + ANO.format(momento) + " · " + HORA.format(momento);
    }

    /**
     * O limite da triagem (RN-24). O prazo de uma sinalização do fim de semana vence à 0h, que na tela seria
     * lido como o começo do dia; por isso mostra o dia anterior, até o fim dele.
     */
    String limite(Instant limite) {
        ZonedDateTime momento = limite.atZone(fuso);
        if (momento.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            return dia(momento.toLocalDate().minusDays(1)) + " · fim do dia";
        }
        return dia(momento.toLocalDate()) + " · " + HORA.format(momento);
    }

    /** O início do dia no fuso da escola, para os filtros de período. */
    Instant inicioDo(LocalDate dia) {
        return dia.atStartOfDay(fuso).toInstant();
    }

    private static String dia(LocalDate data) {
        return DIA.format(data).replace(".", "");
    }
}
