package br.ufg.inf.labsimulacoes.cenarios.dominio;

/** RF-08: quem envia o cenário à curadoria é o autor. */
public class EnvioNaoPermitido extends RuntimeException {

    public EnvioNaoPermitido(Long id) {
        super("Só o autor envia o cenário " + id + " à curadoria (RF-08)");
    }
}
