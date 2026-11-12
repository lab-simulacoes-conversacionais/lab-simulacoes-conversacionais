package br.ufg.inf.labsimulacoes.sessoes.dominio;

/**
 * A pergunta anterior da turma ainda espera a resposta do personagem. Uma pergunta por vez: duas
 * seguidas, como um Enter repetido, deixariam duas perguntas para uma resposta só.
 */
public class PerguntaSemResposta extends RuntimeException {

    public PerguntaSemResposta(Long sessaoId) {
        super("A pergunta anterior da sessão " + sessaoId + " ainda espera a resposta do personagem");
    }
}
