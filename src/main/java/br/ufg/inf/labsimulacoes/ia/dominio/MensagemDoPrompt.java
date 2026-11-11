package br.ufg.inf.labsimulacoes.ia.dominio;

/** Uma mensagem depois das instruções: da turma (usuário) ou do personagem (assistente). */
public record MensagemDoPrompt(Papel papel, String texto) {

    public enum Papel {
        USUARIO,
        ASSISTENTE
    }

    public MensagemDoPrompt {
        if (papel == null) {
            throw new IllegalArgumentException("O papel da mensagem é obrigatório");
        }
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("O texto da mensagem é obrigatório");
        }
    }

    public static MensagemDoPrompt doUsuario(String texto) {
        return new MensagemDoPrompt(Papel.USUARIO, texto);
    }

    public static MensagemDoPrompt doAssistente(String texto) {
        return new MensagemDoPrompt(Papel.ASSISTENTE, texto);
    }
}
