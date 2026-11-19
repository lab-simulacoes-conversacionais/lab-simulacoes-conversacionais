package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

/**
 * Porta de persistência das perguntas de teste (RF-09). A implementação fica na infraestrutura.
 *
 * <p>Cada curador tem a sua rodada em cada versão: as perguntas que ele fez desde a última devolução.
 * A devolução fecha a rodada de todos os curadores daquela versão ({@link #fecharRodada}), inclusive as
 * perguntas ainda pendentes, porque a versão volta a rascunho na mesma linha e o conteúdo pode mudar
 * (RN-07).
 *
 * <p>São duas contagens da rodada em aberto: {@link #ocupadas} para o teto (RNF-13), com todas as
 * perguntas, e {@link #contar} para o RN-06, só com as que testaram o personagem.
 */
public interface PerguntasDeTeste {

    /** RN-06: para aprovar, o curador testa o cenário com pelo menos 5 perguntas. */
    int MINIMO_PARA_APROVAR = 5;

    /**
     * Grava a reserva ({@link PerguntaDeTeste.Situacao#PENDENTE}) na rodada em aberto e devolve com o id.
     * Quem chama trava o cenário e confere o teto antes, na mesma transação.
     */
    PerguntaDeTeste reservar(PerguntaDeTeste reservada);

    /**
     * Grava o resultado na mesma pergunta reservada, sem mexer na rodada: uma pergunta fechada por uma
     * devolução continua fechada.
     *
     * @return a pergunta como ficou, com {@link PerguntaDeTeste#rodadaFechada()} atualizado
     * @throws IllegalStateException se a pergunta não existir ou já tiver resultado
     */
    PerguntaDeTeste concluir(PerguntaDeTeste comResultado);

    /** As perguntas da rodada em aberto do curador na versão, da mais antiga para a mais nova. */
    List<PerguntaDeTeste> daRodada(long versaoId, long curadorId);

    /**
     * Teto (RNF-13): todas as perguntas da rodada em aberto do curador na versão, pendentes, concluídas,
     * bloqueadas e com falha, porque qualquer tentativa gasta algo.
     */
    int ocupadas(long versaoId, long curadorId);

    /**
     * Contagem do RN-06 para o checklist de curadoria (#27): as perguntas da rodada em aberto que o
     * curador que vai aprovar fez na versão e que testaram o personagem, concluídas ou com a resposta
     * bloqueada. Pendentes, falhas, perguntas bloqueadas, perguntas de outros curadores e de rodadas
     * fechadas por devolução não contam.
     */
    int contar(long versaoId, long curadorId);

    /**
     * Na devolução, fecha a rodada em aberto de todos os curadores da versão, ligando as perguntas à
     * devolução, inclusive as pendentes. Elas continuam guardadas, mas deixam de contar e de ocupar vaga.
     *
     * @return quantas perguntas foram fechadas
     */
    int fecharRodada(long versaoId, long devolucaoId);
}
