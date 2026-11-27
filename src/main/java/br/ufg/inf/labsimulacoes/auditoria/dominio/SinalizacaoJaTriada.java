package br.ufg.inf.labsimulacoes.auditoria.dominio;

/** A sinalização já foi confirmada ou descartada; cada uma é triada uma vez só (RF-24). */
public class SinalizacaoJaTriada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SinalizacaoJaTriada(long sinalizacaoId) {
        super("A sinalização " + sinalizacaoId + " já foi triada");
    }
}
