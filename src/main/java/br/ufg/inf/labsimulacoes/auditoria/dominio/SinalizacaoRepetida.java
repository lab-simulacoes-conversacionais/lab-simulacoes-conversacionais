package br.ufg.inf.labsimulacoes.auditoria.dominio;

/** A pessoa já sinalizou esta resposta; cada pessoa sinaliza uma resposta uma vez só (RF-23). */
public class SinalizacaoRepetida extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SinalizacaoRepetida(long mensagemId) {
        super("A resposta " + mensagemId + " já foi sinalizada por esta pessoa");
    }
}
