package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * Por que o cenário suspenso voltou a Aprovado. O nome é o código que vai no evento {@code CenarioReativado},
 * no log de auditoria (RN-35) e na tag da métrica.
 */
public enum MotivoDaReativacao {
    /** RN-23d: o curador descartou a sinalização que suspendeu o cenário. */
    SINALIZACAO_DESCARTADA,
    /**
     * O curador confirmou a sinalização grave do professor com gravidade menor. A suspensão de precaução
     * (RN-23a) cai; se a gravidade final for moderada, ela passa a contar para o RN-23c.
     */
    GRAVIDADE_REBAIXADA,
    /** RN-23b: uma versão nova foi aprovada e passou a ser a vigente. */
    NOVA_VERSAO_APROVADA;

    /**
     * O motivo de uma triagem que deixou o cenário sem motivo de suspensão: o descarte ou a confirmação com
     * gravidade menor.
     *
     * @throws IllegalArgumentException se a sinalização ainda estiver pendente
     */
    public static MotivoDaReativacao pelaTriagem(SinalizacaoDaVersao triada) {
        return switch (triada.situacao()) {
            case DESCARTADA -> SINALIZACAO_DESCARTADA;
            case CONFIRMADA -> GRAVIDADE_REBAIXADA;
            case PENDENTE -> throw new IllegalArgumentException(
                    "A sinalização " + triada.sinalizacaoId() + " ainda não foi triada");
        };
    }
}
