package br.ufg.inf.labsimulacoes.metricas.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Um dia do custo da IA (RNF-13), no fuso da escola: vai da meia-noite local, incluída, à meia-noite
 * seguinte, excluída. Num dia com mudança de horário, o intervalo tem 23 ou 25 horas.
 *
 * @param inicio a meia-noite do dia, incluída
 * @param fim a meia-noite do dia seguinte, excluída
 */
public record DiaDeConsumo(LocalDate dia, Instant inicio, Instant fim) {

    public DiaDeConsumo {
        Objects.requireNonNull(dia, "dia");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        if (!inicio.isBefore(fim)) {
            throw new IllegalArgumentException("O dia precisa começar antes de terminar: " + inicio + " e " + fim);
        }
    }

    /** O dia em que cai o instante, no fuso. */
    public static DiaDeConsumo de(Instant instante, ZoneId fuso) {
        Objects.requireNonNull(instante, "instante");
        Objects.requireNonNull(fuso, "fuso");
        LocalDate dia = LocalDate.ofInstant(instante, fuso);
        return new DiaDeConsumo(
                dia, dia.atStartOfDay(fuso).toInstant(), dia.plusDays(1).atStartOfDay(fuso).toInstant());
    }
}
