package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;

/**
 * Um curador aprovou uma versão do cenário, que passou a ser a vigente (RN-05, RN-08). Vai para o
 * log de auditoria (RN-35).
 *
 * @param cenarioId cenário aprovado
 * @param versaoId versão que passou a ser a vigente
 * @param curadorId quem aprovou
 * @param ocorridoEm momento da aprovação
 */
public record CenarioAprovado(long cenarioId, long versaoId, long curadorId, Instant ocorridoEm) {}
