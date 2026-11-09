package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;

/**
 * O cenário suspenso voltou a Aprovado (RN-23d). Vai para o log de auditoria (RN-35).
 *
 * @param cenarioId cenário reativado
 * @param versaoId versão que volta a valer em sala
 * @param responsavelId o curador cuja decisão reativou o cenário
 * @param motivo por que foi reativado, por exemplo {@code SINALIZACAO_DESCARTADA}; obrigatório
 * @param ocorridoEm momento da reativação
 */
public record CenarioReativado(long cenarioId, long versaoId, long responsavelId, String motivo, Instant ocorridoEm) {}
