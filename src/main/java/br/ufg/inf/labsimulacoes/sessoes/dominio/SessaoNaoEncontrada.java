package br.ufg.inf.labsimulacoes.sessoes.dominio;

public class SessaoNaoEncontrada extends RuntimeException {

    public SessaoNaoEncontrada(long id) {
        super("A sessão " + id + " não existe");
    }
}
