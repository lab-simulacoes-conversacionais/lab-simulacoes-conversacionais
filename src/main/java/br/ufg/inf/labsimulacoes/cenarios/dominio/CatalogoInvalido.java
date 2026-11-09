package br.ufg.inf.labsimulacoes.cenarios.dominio;

/** O arquivo da BNCC não pode ser importado. Nada do catálogo é gravado. */
public class CatalogoInvalido extends RuntimeException {

    public CatalogoInvalido(String mensagem) {
        super(mensagem);
    }

    public CatalogoInvalido(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
