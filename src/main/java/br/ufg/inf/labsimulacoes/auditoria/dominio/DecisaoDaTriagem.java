package br.ufg.inf.labsimulacoes.auditoria.dominio;

/** O que o curador decide sobre uma sinalização pendente (RF-24). O nome é o status que ela passa a ter. */
public enum DecisaoDaTriagem {
    /** O erro existe; a sinalização leva a gravidade final (RN-22). */
    CONFIRMADA,
    /** Não há erro; a sinalização não entra na taxa de erros (RN-29). */
    DESCARTADA
}
