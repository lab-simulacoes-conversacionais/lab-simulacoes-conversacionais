package br.ufg.inf.labsimulacoes.ia;

/**
 * Sugestão de cenário gerada pela IA.
 *
 * @param metadadosId linha de resposta_ia_meta com o rastro da geração (RNF-10)
 */
public record SugestaoDeCenario(RoteiroSugerido roteiro, long metadadosId) {

    public SugestaoDeCenario {
        if (roteiro == null) {
            throw new IllegalArgumentException("O roteiro sugerido é obrigatório");
        }
    }
}
