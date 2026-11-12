package br.ufg.inf.labsimulacoes.sessoes.dominio;

/**
 * O personagem ainda está respondendo na sessão: um segundo canal, como o de uma tela que reconectou,
 * espera o fim da resposta atual, para o modelo não ser chamado duas vezes para a mesma pergunta.
 */
public class RespostaEmAndamento extends RuntimeException {

    public RespostaEmAndamento(long sessaoId) {
        super("O personagem ainda está respondendo na sessão " + sessaoId);
    }
}
