package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PendenciaDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RoteiroDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * O que o editor de cenário envia e mostra (RF-06), inclusive o que ainda não passou na validação, para
 * o formulário voltar com o que foi digitado.
 *
 * <p>As listas do roteiro (objetivos, limites, fontes e perguntas) chegam como texto, com um item por
 * linha, como no protótipo. As habilidades chegam como códigos, um por campo.
 *
 * <p>A etapa é sempre EF: o catálogo carregado é o do Ensino Fundamental.
 */
record FormularioDoCenario(
        String titulo,
        String personagem,
        String papelDaIa,
        String contexto,
        String objetivos,
        String componente,
        Integer ano,
        List<String> habilidades,
        String limitesDeAssunto,
        String fontes,
        String faixaEtaria,
        Integer duracaoPrevistaMin,
        String perguntasDisparadoras) {

    static final String ETAPA = "EF";

    /** Valor inicial do controle de duração, como no protótipo. */
    static final int DURACAO_SUGERIDA_MIN = 20;

    FormularioDoCenario {
        habilidades = habilidades == null ? List.of() : List.copyOf(habilidades);
    }

    static FormularioDoCenario vazio() {
        return new FormularioDoCenario(
                "", "", "", "", "", "", null, List.of(), "", "", "", DURACAO_SUGERIDA_MIN, "");
    }

    static FormularioDoCenario de(ConteudoDoCenario conteudo) {
        RoteiroDoCenario roteiro = conteudo.roteiro();
        VinculoCurricular curriculo = conteudo.curriculo();
        return new FormularioDoCenario(
                conteudo.titulo(),
                roteiro.personagem(),
                roteiro.papelDaIa(),
                roteiro.contexto(),
                linhas(roteiro.objetivos()),
                curriculo.componente(),
                curriculo.ano(),
                curriculo.habilidades(),
                linhas(roteiro.limitesDeAssunto()),
                linhas(roteiro.fontes()),
                conteudo.faixaEtaria(),
                conteudo.duracaoPrevistaMin(),
                linhas(roteiro.perguntasDisparadoras()));
    }

    /**
     * @throws IllegalArgumentException se faltar o título ou algum valor estiver fora da faixa, com a
     *     mensagem do domínio, que já é escrita para o autor
     */
    ConteudoDoCenario paraConteudo() {
        return paraConteudo(titulo);
    }

    /**
     * O que ainda falta para ir à curadoria, na ordem das etapas do editor (RN-02, RN-03). O título
     * entra aqui também, porque o domínio já o exige no rascunho.
     */
    List<ItemPendente> pendencias() {
        var itens = new ArrayList<ItemPendente>();
        boolean semTitulo = titulo == null || titulo.isBlank();
        if (semTitulo) {
            itens.add(new ItemPendente("Título do cenário", 1, "titulo"));
        }
        try {
            // O título provisório só serve para conferir o resto; nada é gravado aqui.
            paraConteudo(semTitulo ? "provisório" : titulo).pendencias().stream()
                    .map(ItemPendente::de)
                    .forEach(itens::add);
        } catch (IllegalArgumentException valorInvalido) {
            itens.add(new ItemPendente(valorInvalido.getMessage(), 1, "titulo"));
        }
        itens.sort((um, outro) -> Integer.compare(um.etapa(), outro.etapa()));
        return itens;
    }

    private ConteudoDoCenario paraConteudo(String tituloUsado) {
        var roteiro = new RoteiroDoCenario(
                personagem,
                papelDaIa,
                contexto,
                itens(objetivos),
                itens(limitesDeAssunto),
                itens(fontes),
                itens(perguntasDisparadoras));
        var curriculo = new VinculoCurricular(componente, ETAPA, ano, habilidades);
        return new ConteudoDoCenario(tituloUsado, roteiro, curriculo, faixaEtaria, duracaoPrevistaMin);
    }

    private static List<String> itens(String texto) {
        return texto == null ? List.of() : texto.lines().map(String::strip).filter(linha -> !linha.isEmpty()).toList();
    }

    private static String linhas(List<String> itens) {
        return String.join("\n", itens);
    }

    /**
     * Um item que falta, com a etapa do editor e o campo onde ele é preenchido, para a lista levar o
     * autor direto ao lugar certo.
     *
     * @param etapa de 1 a 4, na ordem do editor
     * @param campo id do campo no formulário
     */
    record ItemPendente(String descricao, int etapa, String campo) {

        static ItemPendente de(PendenciaDoCenario pendencia) {
            String descricao = maiuscula(pendencia.descricao());
            return switch (pendencia) {
                case PERSONAGEM -> new ItemPendente(descricao, 1, "personagem");
                case PAPEL_DA_IA -> new ItemPendente(descricao, 1, "papelDaIa");
                case CONTEXTO -> new ItemPendente(descricao, 1, "contexto");
                case OBJETIVO -> new ItemPendente(descricao, 2, "objetivos");
                case COMPONENTE -> new ItemPendente(descricao, 2, "componente");
                case ETAPA, ANO -> new ItemPendente(descricao, 2, "ano");
                case HABILIDADE_BNCC -> new ItemPendente(descricao, 2, "bncc-busca");
                case LIMITES_DE_ASSUNTO -> new ItemPendente(descricao, 3, "limitesDeAssunto");
                case FONTE -> new ItemPendente(descricao, 3, "fontes");
                case FAIXA_ETARIA -> new ItemPendente(descricao, 3, "faixaEtaria");
                case DURACAO_PREVISTA -> new ItemPendente(descricao, 3, "duracaoPrevistaMin");
            };
        }

        private static String maiuscula(String texto) {
            return texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
        }
    }
}
