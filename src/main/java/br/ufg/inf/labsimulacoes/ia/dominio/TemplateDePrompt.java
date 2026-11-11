package br.ufg.inf.labsimulacoes.ia.dominio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Um template de prompt versionado, lido de um arquivo fora do código (RNF-11). O formato está em
 * src/main/resources/prompts/README.md: um cabeçalho com a versão e seções abertas por {@code ## nome}.
 *
 * @param versao identificador estável, gravado nos metadados de cada resposta
 * @param secoes o texto de cada seção, pelo nome
 */
public record TemplateDePrompt(String versao, Map<String, String> secoes) {

    private static final String DELIMITADOR = "---";
    private static final String INICIO_DE_SECAO = "## ";

    public TemplateDePrompt {
        if (versao == null || versao.isBlank()) {
            throw new IllegalArgumentException("A versão do template é obrigatória");
        }
        if (secoes == null || secoes.isEmpty()) {
            throw new IllegalArgumentException("O template " + versao + " precisa de ao menos uma seção");
        }
        secoes = Map.copyOf(secoes);
    }

    /**
     * @throws IllegalStateException se o template não tiver a seção
     */
    public String secao(String nome) {
        String texto = secoes.get(nome);
        if (texto == null) {
            throw new IllegalStateException("O template " + versao + " não tem a seção \"" + nome + "\"");
        }
        return texto;
    }

    /**
     * Lê o conteúdo de um arquivo de template.
     *
     * @param versaoEsperada a versão que deu nome ao arquivo; o cabeçalho precisa declarar a mesma
     * @throws IllegalStateException se o arquivo não seguir o formato
     */
    public static TemplateDePrompt ler(String versaoEsperada, String conteudo) {
        String texto = conteudo.replace("\r\n", "\n");
        if (texto.startsWith("﻿")) {
            texto = texto.substring(1);
        }
        List<String> linhas = texto.lines().toList();
        if (linhas.isEmpty() || !linhas.getFirst().strip().equals(DELIMITADOR)) {
            throw invalido(versaoEsperada, "o arquivo precisa começar com o cabeçalho entre linhas \"---\"");
        }

        String versao = null;
        int linha = 1;
        while (linha < linhas.size() && !linhas.get(linha).strip().equals(DELIMITADOR)) {
            String campo = linhas.get(linha).strip();
            if (campo.startsWith("versao:")) {
                versao = campo.substring("versao:".length()).strip();
            }
            linha++;
        }
        if (linha == linhas.size()) {
            throw invalido(versaoEsperada, "o cabeçalho não foi fechado com \"---\"");
        }
        if (versao == null || versao.isEmpty()) {
            throw invalido(versaoEsperada, "o cabeçalho não declara a versão");
        }
        if (!versao.equals(versaoEsperada)) {
            throw invalido(versaoEsperada, "o cabeçalho declara a versão " + versao);
        }

        Map<String, String> secoes = new LinkedHashMap<>();
        String atual = null;
        StringBuilder corpo = new StringBuilder();
        for (String l : linhas.subList(linha + 1, linhas.size())) {
            if (l.startsWith(INICIO_DE_SECAO)) {
                guardar(versao, secoes, atual, corpo);
                atual = l.substring(INICIO_DE_SECAO.length()).strip();
                if (atual.isEmpty() || secoes.containsKey(atual)) {
                    throw invalido(versao, "seção sem nome ou repetida: \"" + l + "\"");
                }
                corpo.setLength(0);
            } else if (atual == null) {
                if (!l.isBlank()) {
                    throw invalido(versao, "há texto fora das seções: \"" + l.strip() + "\"");
                }
            } else {
                corpo.append(l).append('\n');
            }
        }
        guardar(versao, secoes, atual, corpo);
        if (secoes.isEmpty()) {
            throw invalido(versao, "o arquivo não tem nenhuma seção \"## nome\"");
        }
        return new TemplateDePrompt(versao, secoes);
    }

    private static void guardar(String versao, Map<String, String> secoes, String nome, StringBuilder corpo) {
        if (nome == null) {
            return;
        }
        String texto = corpo.toString().strip();
        if (texto.isEmpty()) {
            throw invalido(versao, "a seção \"" + nome + "\" está vazia");
        }
        secoes.put(nome, texto);
    }

    private static IllegalStateException invalido(String versao, String motivo) {
        return new IllegalStateException("Template de prompt " + versao + " inválido: " + motivo);
    }
}
