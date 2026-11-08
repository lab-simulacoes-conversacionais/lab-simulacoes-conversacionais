package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;

/**
 * Um curador devolveu uma versão do cenário, que voltou a Rascunho (RN-07). Vai para o log de
 * auditoria (RN-35).
 *
 * @param cenarioId cenário devolvido
 * @param versaoId versão devolvida
 * @param curadorId quem devolveu
 * @param comentario o que precisa mudar, obrigatório pelo RN-07
 * @param ocorridoEm momento da devolução
 */
public record CenarioDevolvido(long cenarioId, long versaoId, long curadorId, String comentario, Instant ocorridoEm) {}
