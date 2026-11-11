package br.ufg.inf.labsimulacoes.cenarios.dominio;

/** A pessoa não pode aprovar ou devolver este cenário (RN-05, RN-36). */
public class DecisaoDeCuradoriaNaoPermitida extends RuntimeException {

    public DecisaoDeCuradoriaNaoPermitida(String mensagem) {
        super(mensagem);
    }
}
