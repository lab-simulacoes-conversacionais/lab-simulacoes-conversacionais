package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * Separado do status do cenário porque, ao editar um cenário aprovado, a nova versão fica em rascunho
 * enquanto a versão vigente continua valendo em sala (RN-08).
 */
public enum StatusDaVersao {
    RASCUNHO,
    EM_CURADORIA,
    APROVADA
}
