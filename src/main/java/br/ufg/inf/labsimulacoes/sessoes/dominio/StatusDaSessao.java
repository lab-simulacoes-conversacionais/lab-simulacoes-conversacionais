package br.ufg.inf.labsimulacoes.sessoes.dominio;

/** Ciclo de vida da sessão (Stack, seção 5). Encerrada é um estado final (RN-19). */
public enum StatusDaSessao {
    EM_ANDAMENTO,
    PAUSADA,
    ENCERRADA
}
