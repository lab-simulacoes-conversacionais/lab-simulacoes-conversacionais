package br.ufg.inf.labsimulacoes.sessoes.dominio;

/** RN-19: sessão encerrada não pode ser reaberta; para continuar o tema, inicia-se outra. */
public class SessaoEncerrada extends RuntimeException {

    public SessaoEncerrada(Long id, String acao) {
        super("A sessão " + id + " já foi encerrada e não dá para " + acao
                + ". Para continuar o tema, inicie uma nova sessão (RN-19)");
    }
}
