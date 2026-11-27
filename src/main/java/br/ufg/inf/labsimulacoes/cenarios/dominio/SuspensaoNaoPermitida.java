package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * RN-23: só o cenário Aprovado é suspenso (Figura 3 dos requisitos). Quem suspende são as regras de gravidade,
 * que conferem o status antes; isto só acontece por erro de programação.
 */
public class SuspensaoNaoPermitida extends RuntimeException {

    public SuspensaoNaoPermitida(Long id, StatusDoCenario status) {
        super("O cenário " + id + " só pode ser suspenso se estiver aprovado, e está em " + status);
    }
}
