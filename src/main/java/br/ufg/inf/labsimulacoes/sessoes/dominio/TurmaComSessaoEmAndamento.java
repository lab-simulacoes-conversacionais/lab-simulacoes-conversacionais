package br.ufg.inf.labsimulacoes.sessoes.dominio;

/** RN-10: cada turma tem no máximo uma sessão em andamento. */
public class TurmaComSessaoEmAndamento extends RuntimeException {

    public TurmaComSessaoEmAndamento(long turmaId) {
        super("A turma " + turmaId + " já tem uma sessão em andamento. Pause ou encerre essa sessão primeiro (RN-10)");
    }
}
