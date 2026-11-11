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
}
