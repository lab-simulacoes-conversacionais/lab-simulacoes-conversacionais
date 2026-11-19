package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

/**
 * Porta de persistência das perguntas de teste (RF-09). A implementação fica na infraestrutura.
 *
 * <p>Cada curador tem a sua rodada em cada versão: as perguntas que ele fez desde a última devolução.
 * A devolução fecha a rodada de todos os curadores daquela versão ({@link #fecharRodada}), porque a
 * versão volta a rascunho na mesma linha e o conteúdo pode mudar (RN-07).
 */
public interface PerguntasDeTeste {

    /** RN-06: para aprovar, o curador testa o cenário com pelo menos 5 perguntas. */
    int MINIMO_PARA_APROVAR = 5;

    /** Grava a pergunta na rodada em aberto e devolve com o id. */
    PerguntaDeTeste gravar(PerguntaDeTeste pergunta);

    /** As perguntas da rodada em aberto do curador na versão, da mais antiga para a mais nova. */
    List<PerguntaDeTeste> daRodada(long versaoId, long curadorId);

    /**
     * Contagem do RN-06 para o checklist de curadoria (#27): as perguntas da rodada em aberto que o
     * curador que vai aprovar fez na versão. A pergunta bloqueada pelo filtro não conta; a resposta
     * bloqueada conta. Perguntas de outros curadores e de rodadas fechadas por devolução não contam.
     */
    int contar(long versaoId, long curadorId);

    /**
     * Na devolução, fecha a rodada em aberto de todos os curadores da versão, ligando as perguntas à
     * devolução. Elas continuam guardadas, mas deixam de contar.
     *
     * @return quantas perguntas foram fechadas
     */
    int fecharRodada(long versaoId, long devolucaoId);
}
