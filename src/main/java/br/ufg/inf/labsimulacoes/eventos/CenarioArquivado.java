package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;

/**
 * O cenário saiu da biblioteca, mas o histórico continua disponível (RN-09). Vai para o log de
 * auditoria (RN-35).
 *
 * @param cenarioId cenário arquivado
 * @param arquivadoPorId quem arquivou
 * @param ocorridoEm momento do arquivamento
 */
public record CenarioArquivado(long cenarioId, long arquivadoPorId, Instant ocorridoEm) {}
