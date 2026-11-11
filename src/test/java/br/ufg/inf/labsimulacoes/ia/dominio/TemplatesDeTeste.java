package br.ufg.inf.labsimulacoes.ia.dominio;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Os templates de verdade, lidos do classpath sem o Spring, para os testes sem contexto. */
public final class TemplatesDeTeste {

    public static final String PERSONAGEM = "personagem-v1";
    public static final String SUGESTAO = "sugestao-v1";

    private TemplatesDeTeste() {}

    public static MontadorComTemplates montador() {
        return new MontadorComTemplates(ler(PERSONAGEM), ler(SUGESTAO));
    }

    public static TemplateDePrompt ler(String versao) {
        try (InputStream arquivo = TemplatesDeTeste.class.getResourceAsStream("/prompts/" + versao + ".md")) {
            if (arquivo == null) {
                throw new IllegalStateException("Template de teste ausente: " + versao);
            }
            return TemplateDePrompt.ler(versao, new String(arquivo.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
