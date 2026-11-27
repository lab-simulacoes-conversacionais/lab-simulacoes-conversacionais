package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * RN-23d: só o cenário Suspenso volta a Aprovado por reativação. Quem reativa são as regras de gravidade, que
 * conferem o status antes; isto só acontece por erro de programação.
 */
public class ReativacaoNaoPermitida extends RuntimeException {

    public ReativacaoNaoPermitida(Long id, StatusDoCenario status) {
        super("O cenário " + id + " só pode ser reativado se estiver suspenso, e está em " + status);
    }
}
