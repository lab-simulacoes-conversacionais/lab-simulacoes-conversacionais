package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * RNF-13: quantas perguntas de teste cada curador faz em uma versão. Cada pergunta gasta tokens do
 * provedor, então passar do teto é recusado antes de qualquer chamada.
 *
 * <p>Conta o mesmo que o RN-06 ({@link PerguntasDeTeste#contar}): a pergunta bloqueada, que não chega
 * ao modelo, não conta. Uma devolução abre rodada nova, e a contagem recomeça.
 *
 * <p>O teto é conferido antes de chamar o provedor, sem trava, para a geração não ficar dentro de uma
 * transação. Dois envios simultâneos do mesmo curador podem passar do teto por uma pergunta; para um
 * limite de custo, isso é aceitável.
 *
 * @param perguntasPorVersao quantas perguntas que contam cabem em uma rodada
 */
public record LimiteDoTeste(int perguntasPorVersao) {

    public LimiteDoTeste {
        if (perguntasPorVersao < PerguntasDeTeste.MINIMO_PARA_APROVAR) {
            throw new IllegalArgumentException("O teto precisa caber as " + PerguntasDeTeste.MINIMO_PARA_APROVAR
                    + " perguntas que o RN-06 exige: " + perguntasPorVersao);
        }
    }

    /**
     * @param jaFeitas quantas perguntas que contam o curador já fez na rodada, sem esta
     * @throws LimiteDoTesteAtingido se não couber mais uma
     */
    public void exigirDentro(int jaFeitas) {
        if (jaFeitas >= perguntasPorVersao) {
            throw new LimiteDoTesteAtingido(perguntasPorVersao);
        }
    }
}
