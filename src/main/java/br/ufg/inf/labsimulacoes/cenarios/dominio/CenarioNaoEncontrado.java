package br.ufg.inf.labsimulacoes.cenarios.dominio;

public class CenarioNaoEncontrado extends RuntimeException {

    public CenarioNaoEncontrado(long id) {
        super("O cenário " + id + " não existe");
    }
}
