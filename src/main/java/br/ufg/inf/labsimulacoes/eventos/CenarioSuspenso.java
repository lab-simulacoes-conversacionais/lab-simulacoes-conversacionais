package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;

/**
 * O cenário foi suspenso pelas regras de gravidade (RN-23). Publicado por Cenários, só quando o cenário passa
 * de Aprovado a Suspenso: a mesma sinalização entregue de novo não o publica outra vez.
 *
 * <p>Quem escuta: Sessões pausa todas as sessões em andamento do cenário, de qualquer versão, com o motivo
 * {@code CENARIO_SUSPENSO} (RN-25), e a Auditoria registra {@code SUSPENDER_CENARIO} no log (RN-35). A
 * suspensão é automática, mas o log precisa de um usuário: fica registrado quem a disparou. Só ids e códigos
 * (RNF-01).
 *
 * @param cenarioId cenário suspenso
 * @param versaoId a versão vigente, em que o erro foi sinalizado
 * @param responsavelId o professor que marcou a sinalização como grave (RN-23a) ou o curador que
 *     confirmou o erro (RN-23b, RN-23c)
 * @param motivo regra que suspendeu: {@code SINALIZACAO_GRAVE}, {@code ERRO_GRAVE_CONFIRMADO} ou
 *     {@code TRES_ERROS_MODERADOS}; obrigatório
 * @param ocorridoEm momento da sinalização ou da triagem que suspendeu
 */
public record CenarioSuspenso(long cenarioId, long versaoId, long responsavelId, String motivo, Instant ocorridoEm) {}
