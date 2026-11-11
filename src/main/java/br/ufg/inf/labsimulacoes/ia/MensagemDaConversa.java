package br.ufg.inf.labsimulacoes.ia;

/** Uma fala anterior da sessão, enviada ao modelo como histórico. */
public record MensagemDaConversa(Autor autor, String texto) {

    /** Quem falou. A turma fala pelo professor, que digita as perguntas. */
    public enum Autor {
        TURMA,
        PERSONAGEM
    }

    public MensagemDaConversa {
        if (autor == null) {
            throw new IllegalArgumentException("O autor da mensagem é obrigatório");
        }
        texto = Textos.obrigatorio(texto, "texto da mensagem");
    }
}
