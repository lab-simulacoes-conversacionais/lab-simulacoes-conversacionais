package br.ufg.inf.labsimulacoes.sessoes.dominio;

/**
 * RN-01: só a versão aprovada vigente vai para a sala. Rascunho, em curadoria, suspenso, arquivado e
 * cenário inexistente caem aqui.
 */
public class CenarioSemVersaoAprovada extends RuntimeException {

    public CenarioSemVersaoAprovada(long cenarioId) {
        super("O cenário " + cenarioId + " não está aprovado e não pode ir para a sala (RN-01)");
    }
}
