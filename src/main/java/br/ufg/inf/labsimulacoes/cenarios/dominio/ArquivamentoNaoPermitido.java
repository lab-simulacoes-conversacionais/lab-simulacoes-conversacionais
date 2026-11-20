package br.ufg.inf.labsimulacoes.cenarios.dominio;

/** RF-13: só sai da biblioteca o cenário Aprovado ou Suspenso (Figura 3 dos requisitos). */
public class ArquivamentoNaoPermitido extends RuntimeException {

    public ArquivamentoNaoPermitido(Long id, StatusDoCenario status) {
        super("O cenário " + id + " só pode ser arquivado se estiver aprovado ou suspenso, e está em " + status);
    }
}
