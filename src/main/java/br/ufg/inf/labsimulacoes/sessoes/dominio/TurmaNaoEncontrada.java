package br.ufg.inf.labsimulacoes.sessoes.dominio;

/** A sessão precisa de uma turma cadastrada pelo administrador (RF-04, RF-14). */
public class TurmaNaoEncontrada extends RuntimeException {

    public TurmaNaoEncontrada(long turmaId) {
        super("A turma " + turmaId + " não existe");
    }
}
