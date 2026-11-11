package br.ufg.inf.labsimulacoes.cenarios.dominio;

/** RN-07: a devolução precisa dizer ao autor o que mudar. */
public class ComentarioObrigatorio extends RuntimeException {

    public ComentarioObrigatorio() {
        super("Para devolver o cenário, escreva um comentário dizendo o que precisa mudar (RN-07)");
    }
}
