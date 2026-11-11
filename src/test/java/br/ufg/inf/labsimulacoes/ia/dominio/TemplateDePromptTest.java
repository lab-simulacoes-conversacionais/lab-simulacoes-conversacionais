package br.ufg.inf.labsimulacoes.ia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TemplateDePromptTest {

    private static final String VALIDO = """
            ---
            versao: teste-v1
            descricao: só para os testes
            ---

            ## instrucoes

            Você é {{personagem}}.
            - Uma regra.

            ## abertura
            Apresente-se.
            """;

    @Test
    void leAVersaoEAsSecoes() {
        TemplateDePrompt template = TemplateDePrompt.ler("teste-v1", VALIDO);

        assertThat(template.versao()).isEqualTo("teste-v1");
        assertThat(template.secoes()).containsOnlyKeys("instrucoes", "abertura");
        assertThat(template.secao("instrucoes")).isEqualTo("Você é {{personagem}}.\n- Uma regra.");
        assertThat(template.secao("abertura")).isEqualTo("Apresente-se.");
    }

    /** O Git no Windows pode trazer o arquivo com CRLF, e editores às vezes gravam o BOM. */
    @Test
    void aceitaCrlfEBom() {
        TemplateDePrompt template = TemplateDePrompt.ler("teste-v1", "﻿" + VALIDO.replace("\n", "\r\n"));

        assertThat(template.secao("instrucoes")).isEqualTo("Você é {{personagem}}.\n- Uma regra.");
    }

    @Test
    void secaoInexistenteFalhaComONome() {
        TemplateDePrompt template = TemplateDePrompt.ler("teste-v1", VALIDO);

        assertThatThrownBy(() -> template.secao("encerramento"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("O template teste-v1 não tem a seção \"encerramento\"");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ''                                                   | precisa começar com o cabeçalho
            '## instrucoes\nTexto'                               | precisa começar com o cabeçalho
            '---\nversao: teste-v1\n## instrucoes\nTexto'        | não foi fechado
            '---\ndescricao: x\n---\n## instrucoes\nTexto'       | não declara a versão
            '---\nversao:\n---\n## instrucoes\nTexto'            | não declara a versão
            '---\nversao: teste-v2\n---\n## instrucoes\nTexto'   | declara a versão teste-v2
            '---\nversao: teste-v1\n---\nSolto\n## instrucoes\nT' | texto fora das seções: "Solto"
            '---\nversao: teste-v1\n---\n## a\nT\n## a\nU'       | seção sem nome ou repetida
            '---\nversao: teste-v1\n---\n## \nT'                 | seção sem nome ou repetida
            '---\nversao: teste-v1\n---\n## a\n\n## b\nT'        | a seção "a" está vazia
            '---\nversao: teste-v1\n---\n\n'                     | não tem nenhuma seção
            """)
    void arquivoForaDoFormatoFalhaComOMotivo(String conteudo, String motivo) {
        assertThatThrownBy(() -> TemplateDePrompt.ler("teste-v1", conteudo.replace("\\n", "\n")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Template de prompt teste-v")
                .hasMessageContaining(motivo);
    }

    @Test
    void construtorExigeVersaoESecoes() {
        assertThatThrownBy(() -> new TemplateDePrompt(" ", Map.of("a", "b"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemplateDePrompt(null, Map.of("a", "b"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemplateDePrompt("v1", Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemplateDePrompt("v1", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secoesNaoMudamDepoisDeCriado() {
        Map<String, String> secoes = new HashMap<>(Map.of("instrucoes", "Texto"));
        TemplateDePrompt template = new TemplateDePrompt("v1", secoes);

        secoes.put("instrucoes", "Outro");

        assertThat(template.secao("instrucoes")).isEqualTo("Texto");
    }
}
