package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * RNF-13: quantas perguntas de teste cada curador faz numa rodada de uma versão. Cada pergunta gasta
 * algo do provedor, então passar do teto é recusado antes de qualquer chamada.
 *
 * <p>Conta todas as perguntas da rodada ({@link PerguntasDeTeste#ocupadas}): pendentes, concluídas,
 * bloqueadas e com falha. É outra contagem que a do RN-06 ({@link PerguntasDeTeste#contar}). Uma devolução
 * abre rodada nova, e as duas recomeçam.
 *
 * <p>O teto é exato também com pedidos ao mesmo tempo: a vaga é reservada com o cenário travado, antes
 * de chamar a IA. Uma pergunta pendente presa por uma queda da aplicação continua ocupando a sua vaga até
 * a próxima devolução; ela não conta para o RN-06.
 *
 * @param perguntasPorVersao quantas perguntas cabem em uma rodada
 */
public record LimiteDoTeste(int perguntasPorVersao) {

    public LimiteDoTeste {
        if (perguntasPorVersao < PerguntasDeTeste.MINIMO_PARA_APROVAR) {
            throw new IllegalArgumentException("O teto precisa caber as " + PerguntasDeTeste.MINIMO_PARA_APROVAR
                    + " perguntas que o RN-06 exige: " + perguntasPorVersao);
        }
    }

    /**
     * @param ocupadas quantas perguntas a rodada do curador já tem, de qualquer situação, sem esta
     * @throws LimiteDoTesteAtingido se não couber mais uma
     */
    public void exigirDentro(int ocupadas) {
        if (ocupadas >= perguntasPorVersao) {
            throw new LimiteDoTesteAtingido(perguntasPorVersao);
        }
    }
}
