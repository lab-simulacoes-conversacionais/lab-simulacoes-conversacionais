package br.ufg.inf.labsimulacoes.sessoes.dominio;

/**
 * RN-18: a turma já fez todas as perguntas que a sessão aceita. O envio fica bloqueado e o sistema
 * sugere passar ao quiz.
 */
public class LimiteDePerguntasAtingido extends RuntimeException {

    public LimiteDePerguntasAtingido(Long sessaoId, int limite) {
        super("A sessão " + sessaoId + " já recebeu as " + limite + " perguntas permitidas (RN-18)");
    }
}
