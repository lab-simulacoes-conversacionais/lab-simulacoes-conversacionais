package br.ufg.inf.labsimulacoes.acesso.dominio;

/** Porta de persistência dos parâmetros. A implementação fica na infraestrutura. */
public interface RegistroDeParametros {

    /** Parâmetro que o administrador nunca definiu vem com o valor de {@link Parametros#padrao()}. */
    Parametros carregar();

    void salvar(Parametros parametros);
}
