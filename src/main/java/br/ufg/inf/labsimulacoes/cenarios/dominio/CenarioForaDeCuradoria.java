package br.ufg.inf.labsimulacoes.cenarios.dominio;

/** Só se aprova ou devolve uma versão que está em curadoria. */
public class CenarioForaDeCuradoria extends RuntimeException {

    public CenarioForaDeCuradoria(Long id, StatusDaVersao status) {
        super("O cenário " + id + " só pode ser aprovado ou devolvido em curadoria, e sua versão está em " + status);
    }
}
