package br.ufg.inf.labsimulacoes.cenarios.dominio;

public class VersaoNaoEncontrada extends RuntimeException {

    public VersaoNaoEncontrada(long cenarioId, int numero) {
        super("O cenário " + cenarioId + " não tem a versão " + numero);
    }
}
