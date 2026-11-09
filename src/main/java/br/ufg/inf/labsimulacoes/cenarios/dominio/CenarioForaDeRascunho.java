package br.ufg.inf.labsimulacoes.cenarios.dominio;

public class CenarioForaDeRascunho extends RuntimeException {

    public CenarioForaDeRascunho(Long id, StatusDaVersao status) {
        super("O cenário " + id + " só pode ser alterado ou enviado em rascunho, e sua versão está em " + status);
    }
}
