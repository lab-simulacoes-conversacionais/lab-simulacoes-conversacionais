package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * Qual regra de gravidade suspendeu o cenário (RN-23). O nome é o código que vai no evento
 * {@code CenarioSuspenso}, no log de auditoria (RN-35) e na tag da métrica.
 */
public enum MotivoDaSuspensao {
    /** RN-23a: o professor marcou a sinalização como grave, e ela ainda não foi triada. */
    SINALIZACAO_GRAVE,
    /** RN-23b: o curador confirmou um erro grave; vale até uma versão nova ser aprovada. */
    ERRO_GRAVE_CONFIRMADO,
    /** RN-23c: o curador confirmou três erros moderados na mesma versão. */
    TRES_ERROS_MODERADOS
}
