package br.ufg.inf.labsimulacoes.sessoes.dominio;

/** O controle não vale no estado atual, como pausar uma sessão que já está pausada (RF-17). */
public class SessaoForaDoEstado extends RuntimeException {

    public SessaoForaDoEstado(Long id, String acao, StatusDaSessao status) {
        super("Não dá para " + acao + " a sessão " + id + ", que está " + status);
    }
}
