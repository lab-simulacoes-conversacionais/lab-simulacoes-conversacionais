package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;

/**
 * O cenário suspenso voltou a Aprovado (RN-23b, RN-23d). Publicado por Cenários, só quando o status muda: pela
 * triagem que tira o último motivo de suspensão, ou pela aprovação da versão nova de um cenário suspenso, na
 * mesma transação do {@link CenarioAprovado}.
 *
 * <p>Quem escuta: a Auditoria registra {@code REATIVAR_CENARIO} no log (RN-35). Sessões não escuta: as
 * sessões pausadas pela suspensão continuam pausadas, e quem decide retomar é o professor (RN-25). Só ids e
 * códigos (RNF-01).
 *
 * @param cenarioId cenário reativado
 * @param versaoId versão que volta a valer em sala; a nova, na aprovação
 * @param responsavelId o curador cuja triagem ou aprovação reativou o cenário
 * @param motivo por que foi reativado: {@code SINALIZACAO_DESCARTADA}, {@code GRAVIDADE_REBAIXADA} ou
 *     {@code NOVA_VERSAO_APROVADA}; obrigatório
 * @param ocorridoEm momento da triagem ou da aprovação
 */
public record CenarioReativado(long cenarioId, long versaoId, long responsavelId, String motivo, Instant ocorridoEm) {}
