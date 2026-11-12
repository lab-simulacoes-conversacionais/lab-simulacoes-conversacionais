package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

/**
 * Para onde vai a resposta do personagem enquanto é gerada: só recebe frases que a moderação aprovou
 * (RN-20).
 */
public interface TelaDaResposta {

    /** Uma frase aprovada, na ordem da resposta, com o espaço que a separa da anterior. */
    void exibir(String frase);

    /**
     * Uma frase foi bloqueada. Chega uma vez só, com a sessão já pausada, e depois dela nada mais é
     * exibido: a tela descarta a resposta parcial e mostra o aviso neutro. Não recebe o texto bloqueado.
     */
    void bloquear();
}
