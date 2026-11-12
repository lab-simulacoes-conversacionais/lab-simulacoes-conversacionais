package br.ufg.inf.labsimulacoes.cenarios;

import java.util.Optional;

/**
 * Consulta da versão de um cenário que pode ir para a sala (RN-01), para o módulo Sessões usar ao
 * iniciar uma sessão (RF-14, Stack, seção 4).
 *
 * <p>Exige o perfil Professor ou Curador.
 */
public interface CenariosAprovados {

    /**
     * A versão aprovada vigente do cenário. Vazio se o cenário não existir ou não estiver Aprovado:
     * rascunho, em curadoria, suspenso e arquivado não vão para a sala.
     */
    Optional<VersaoAprovada> versaoVigente(long cenarioId);

    /**
     * Uma versão aprovada pelo id, para a sessão conduzir a conversa com a versão com que começou, mesmo
     * que o cenário já tenha outra vigente (RN-08). Não confere o status atual do cenário: a suspensão
     * pausa as sessões em andamento por evento (RN-25). Vazio se a versão não existir ou não tiver sido
     * aprovada.
     */
    Optional<VersaoAprovada> versaoAprovada(long versaoId);
}
