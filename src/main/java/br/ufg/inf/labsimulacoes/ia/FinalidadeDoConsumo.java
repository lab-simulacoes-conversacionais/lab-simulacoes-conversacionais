package br.ufg.inf.labsimulacoes.ia;

/**
 * Para que a IA foi chamada (RNF-13). Vai com os metadados de cada resposta, junto com a referência que
 * quem chamou informou, para somar o custo por sessão e separar o custo do dia por finalidade. O
 * adaptador grava sem saber o que a referência significa, e por isso não depende dos outros módulos.
 */
public enum FinalidadeDoConsumo {

    /** Fala do personagem numa sessão em sala; a referência é o id da sessão. */
    FALA_EM_SALA,

    /** Pergunta de teste do curador antes de aprovar (RF-09); a referência é o id da versão. */
    TESTE_DO_CURADOR,

    /** Sugestão de cenário (RF-07), sem referência. */
    SUGESTAO
}
