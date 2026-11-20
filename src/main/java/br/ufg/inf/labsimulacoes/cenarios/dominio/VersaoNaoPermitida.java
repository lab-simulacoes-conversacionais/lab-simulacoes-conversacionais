package br.ufg.inf.labsimulacoes.cenarios.dominio;

/** RF-11: a versão nova ou a restauração não pode ser criada agora. A mensagem diz o motivo. */
public class VersaoNaoPermitida extends RuntimeException {

    public VersaoNaoPermitida(String mensagem) {
        super(mensagem);
    }
}
