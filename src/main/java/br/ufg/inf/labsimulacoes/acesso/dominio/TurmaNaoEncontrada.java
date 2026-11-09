package br.ufg.inf.labsimulacoes.acesso.dominio;

public class TurmaNaoEncontrada extends RuntimeException {

    public TurmaNaoEncontrada(long id) {
        super("A turma " + id + " não existe");
    }
}
