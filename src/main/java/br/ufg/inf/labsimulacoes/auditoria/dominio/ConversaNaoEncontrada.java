package br.ufg.inf.labsimulacoes.auditoria.dominio;

/**
 * A sessão não existe ou é de outro professor: para quem não é curador, as duas dão no mesmo, para não
 * revelar que a sessão existe (RN-37).
 */
public class ConversaNaoEncontrada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConversaNaoEncontrada(long sessaoId) {
        super("Conversa da sessão " + sessaoId + " não encontrada");
    }
}
