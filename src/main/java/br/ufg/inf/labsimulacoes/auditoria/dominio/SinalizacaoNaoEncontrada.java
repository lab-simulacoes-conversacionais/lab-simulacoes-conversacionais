package br.ufg.inf.labsimulacoes.auditoria.dominio;

/** A sinalização que o curador quer triar não existe (RF-24). */
public class SinalizacaoNaoEncontrada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SinalizacaoNaoEncontrada(long sinalizacaoId) {
        super("Sinalização " + sinalizacaoId + " não encontrada");
    }
}
