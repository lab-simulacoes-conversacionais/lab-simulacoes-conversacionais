package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;

/**
 * O cenário foi suspenso pelas regras de gravidade (RN-23). Vai para o log de auditoria (RN-35).
 *
 * <p>A suspensão é automática, mas o log precisa de um usuário: fica registrado quem a disparou.
 *
 * @param cenarioId cenário suspenso
 * @param versaoId versão em que o erro foi sinalizado
 * @param responsavelId o professor que marcou a sinalização como grave (RN-23a) ou o curador que
 *     confirmou o erro (RN-23b, RN-23c)
 * @param motivo regra que suspendeu, por exemplo {@code SINALIZACAO_GRAVE} ou
 *     {@code TRES_ERROS_MODERADOS}; obrigatório
 * @param ocorridoEm momento da suspensão
 */
public record CenarioSuspenso(long cenarioId, long versaoId, long responsavelId, String motivo, Instant ocorridoEm) {}
