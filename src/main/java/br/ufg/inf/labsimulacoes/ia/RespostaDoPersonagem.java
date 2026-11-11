package br.ufg.inf.labsimulacoes.ia;

/**
 * Resposta completa do personagem.
 *
 * @param metadadosId linha de resposta_ia_meta, que a mensagem exibida referencia (RNF-10)
 */
public record RespostaDoPersonagem(String texto, long metadadosId) {

    public RespostaDoPersonagem {
        texto = texto == null ? "" : texto.strip();
    }
}
