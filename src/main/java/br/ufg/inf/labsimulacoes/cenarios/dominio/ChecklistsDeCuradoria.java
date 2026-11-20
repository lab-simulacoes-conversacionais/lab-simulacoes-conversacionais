package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Optional;

/**
 * Porta de persistência do checklist de curadoria (RF-10). A implementação fica na infraestrutura.
 *
 * <p>Guarda o que o curador conferiu ao aprovar cada versão: os itens e quantas perguntas de teste ele
 * tinha feito (RN-06). Como a versão aprovada (RN-08), o registro não muda depois de gravado.
 */
public interface ChecklistsDeCuradoria {

    /**
     * Grava a conferência da aprovação, na mesma transação da aprovação.
     *
     * @param conferencia completa: só a aprovação com tudo conferido chega aqui
     * @throws IllegalArgumentException se a conferência não estiver completa; o domínio já recusa a
     *     aprovação antes, então isto só acontece por erro de programação
     */
    void registrar(long versaoId, long curadorId, ConferenciaDaCuradoria conferencia);

    /** A conferência gravada na aprovação da versão. Vazio se a versão não foi aprovada com checklist. */
    Optional<ConferenciaDaCuradoria> daVersao(long versaoId);
}
