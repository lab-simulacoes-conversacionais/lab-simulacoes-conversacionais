package br.ufg.inf.labsimulacoes.sessoes.dominio;

/**
 * O personagem ainda está respondendo na sessão: uma nova pergunta ou uma segunda resposta esperam o
 * fim da atual, para as falas não se misturarem na tela.
 */
public class RespostaEmAndamento extends RuntimeException {

    public RespostaEmAndamento(long sessaoId) {
        super("O personagem ainda está respondendo na sessão " + sessaoId);
    }
}
