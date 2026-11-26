package br.ufg.inf.labsimulacoes.auditoria.dominio;

/**
 * A resposta não existe, não é da IA ou é de uma sessão que a pessoa não pode ver: a sessão de outro
 * professor tem a mesma resposta, para não revelar que existe (RN-37).
 */
public class RespostaNaoEncontrada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RespostaNaoEncontrada(long mensagemId) {
        super("Resposta " + mensagemId + " não encontrada");
    }
}
