package br.ufg.inf.labsimulacoes.sessoes.dominio;

/**
 * Por que a sessão pausou. Cada motivo mostra um aviso diferente ao professor (Stack, seção 5).
 */
public enum MotivoDaPausa {
    /** O professor pausou (RF-17). */
    MANUAL,
    /** Uma pergunta ou resposta foi bloqueada pelo filtro de conteúdo (RN-20). */
    CONTEUDO_BLOQUEADO,
    /** A sessão chegou a 50 minutos (RN-17). */
    LIMITE_50_MINUTOS,
    /** O cenário foi suspenso durante a aula (RN-25). */
    CENARIO_SUSPENSO
}
