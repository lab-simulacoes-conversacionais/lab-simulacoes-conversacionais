package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import br.ufg.inf.labsimulacoes.ia.dominio.TemplateDePrompt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/** Lê os templates de prompt de src/main/resources/prompts, um arquivo por versão. */
final class LeitorDeTemplates {

    static final String PASTA = "classpath:prompts/";
    private static final Pattern VERSAO = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final ResourceLoader carregador;

    LeitorDeTemplates(ResourceLoader carregador) {
        this.carregador = carregador;
    }

    /**
     * @param propriedade a propriedade que escolheu a versão, para a mensagem de erro
     * @throws IllegalStateException se a versão não tiver arquivo ou o arquivo for inválido
     */
    TemplateDePrompt ler(String versao, String propriedade) {
        if (versao == null || !VERSAO.matcher(versao).matches()) {
            throw new IllegalStateException("A versão de prompt \"" + versao + "\", configurada em " + propriedade
                    + ", é inválida: use só letras minúsculas, números e hífen");
        }
        Resource arquivo = carregador.getResource(PASTA + versao + ".md");
        if (!arquivo.exists()) {
            throw new IllegalStateException("Não há template de prompt para a versão \"" + versao
                    + "\", configurada em " + propriedade + ": falta o arquivo prompts/" + versao + ".md");
        }
        try {
            return TemplateDePrompt.ler(versao, arquivo.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o template de prompt " + versao, e);
        }
    }
}
