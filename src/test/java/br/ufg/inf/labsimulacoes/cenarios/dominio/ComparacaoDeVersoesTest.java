package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Comparação de duas versões campo a campo (RF-11), sem banco nem Spring. */
class ComparacaoDeVersoesTest {

    private static final RoteiroDoCenario ROTEIRO = ConteudosDeTeste.ROTEIRO;

    @Test
    void versoesIguaisNaoTemDiferencaMasTrazemTodosOsCampos() {
        var comparacao = ComparacaoDeVersoes.entre(versao(1, ConteudosDeTeste.completo()),
                versao(2, ConteudosDeTeste.completo()));

        assertThat(comparacao.iguais()).isTrue();
        assertThat(comparacao.diferencas()).isEmpty();
        assertThat(comparacao.campos()).extracting(DiferencaDoCampo::campo).containsExactly(CampoDoCenario.values());
    }

    @Test
    void aVersaoDeNumeroMenorFicaSempreComoAAntiga() {
        VersaoDoCenario primeira = versao(1, ConteudosDeTeste.completo());
        VersaoDoCenario terceira = versao(3, comTitulo("Outro título"));

        var comparacao = ComparacaoDeVersoes.entre(terceira, primeira);

        assertThat(comparacao.antiga()).isEqualTo(primeira);
        assertThat(comparacao.nova()).isEqualTo(terceira);
        assertThat(comparacao.diferencas()).singleElement().satisfies(titulo -> {
            assertThat(titulo.campo()).isEqualTo(CampoDoCenario.TITULO);
            assertThat(titulo.antes()).containsExactly("O voo do 14-bis");
            assertThat(titulo.depois()).containsExactly("Outro título");
            assertThat(titulo.removidos()).containsExactly("O voo do 14-bis");
            assertThat(titulo.incluidos()).containsExactly("Outro título");
        });
    }

    @Test
    void listaMostraOQueEntrouEOQueSaiu() {
        var roteiro = new RoteiroDoCenario(ROTEIRO.personagem(), ROTEIRO.papelDaIa(), ROTEIRO.contexto(),
                List.of("Explicar por que o voo por meios próprios foi importante", "Comparar com os irmãos Wright"),
                List.of(), ROTEIRO.fontes(), ROTEIRO.perguntasDisparadoras());

        var comparacao = ComparacaoDeVersoes.entre(versao(1, ConteudosDeTeste.completo()),
                versao(2, comRoteiro(roteiro)));

        assertThat(comparacao.diferencas()).extracting(DiferencaDoCampo::campo)
                .containsExactly(CampoDoCenario.OBJETIVOS, CampoDoCenario.LIMITES_DE_ASSUNTO);
        DiferencaDoCampo objetivos = comparacao.diferencas().get(0);
        assertThat(objetivos.incluidos()).containsExactly("Comparar com os irmãos Wright");
        assertThat(objetivos.removidos()).isEmpty();
        DiferencaDoCampo limites = comparacao.diferencas().get(1);
        assertThat(limites.removidos()).containsExactly("Política partidária");
        assertThat(limites.depois()).isEmpty();
    }

    /** A ordem dos objetivos também é conteúdo, mesmo sem nada entrar ou sair. */
    @Test
    void reordenarContaComoMudanca() {
        var antes = comRoteiro(new RoteiroDoCenario(ROTEIRO.personagem(), ROTEIRO.papelDaIa(), ROTEIRO.contexto(),
                List.of("A", "B"), ROTEIRO.limitesDeAssunto(), ROTEIRO.fontes(), ROTEIRO.perguntasDisparadoras()));
        var depois = comRoteiro(new RoteiroDoCenario(ROTEIRO.personagem(), ROTEIRO.papelDaIa(), ROTEIRO.contexto(),
                List.of("B", "A"), ROTEIRO.limitesDeAssunto(), ROTEIRO.fontes(), ROTEIRO.perguntasDisparadoras()));

        DiferencaDoCampo objetivos = ComparacaoDeVersoes.entre(versao(1, antes), versao(2, depois)).diferencas().get(0);

        assertThat(objetivos.mudou()).isTrue();
        assertThat(objetivos.soMudouAOrdem()).isTrue();
        assertThat(objetivos.incluidos()).isEmpty();
        assertThat(objetivos.removidos()).isEmpty();
    }

    /** Conta as repetições: de dois "A" para um, um "A" saiu. */
    @Test
    void itemRepetidoContaComoMudanca() {
        DiferencaDoCampo diferenca = DiferencaDoCampo.entre(CampoDoCenario.FONTES, List.of("A", "A", "B"), List.of("B", "A"));

        assertThat(diferenca.removidos()).containsExactly("A");
        assertThat(diferenca.incluidos()).isEmpty();
        assertThat(diferenca.soMudouAOrdem()).isFalse();
    }

    @Test
    void campoIgualNaoMudouNemNaOrdem() {
        DiferencaDoCampo igual = DiferencaDoCampo.entre(CampoDoCenario.FONTES, List.of("A"), List.of("A"));

        assertThat(igual.mudou()).isFalse();
        assertThat(igual.soMudouAOrdem()).isFalse();
    }

    @Test
    void camposDeUmValorEOVinculoComABncc() {
        var curriculo = new VinculoCurricular("História", "EF", 9, List.of("EF08HI03", "EF09HI01"));
        var depois = new ConteudoDoCenario("O voo do 14-bis", ROTEIRO, curriculo, null, 30);

        var comparacao = ComparacaoDeVersoes.entre(versao(1, ConteudosDeTeste.completo()), versao(2, depois));

        assertThat(comparacao.diferencas()).extracting(DiferencaDoCampo::campo).containsExactly(
                CampoDoCenario.ANO, CampoDoCenario.HABILIDADES, CampoDoCenario.FAIXA_ETARIA,
                CampoDoCenario.DURACAO_PREVISTA);
        assertThat(comparacao.diferencas().get(0).antes()).containsExactly("8");
        assertThat(comparacao.diferencas().get(0).depois()).containsExactly("9");
        assertThat(comparacao.diferencas().get(1).incluidos()).containsExactly("EF09HI01");
        assertThat(comparacao.diferencas().get(2).depois()).isEmpty();
        assertThat(comparacao.diferencas().get(3).depois()).containsExactly("30");
    }

    @Test
    void cadaCampoTemRotuloParaATela() {
        assertThat(CampoDoCenario.PAPEL_DA_IA.rotulo()).isEqualTo("Papel da IA");
        assertThat(CampoDoCenario.values()).allSatisfy(campo -> assertThat(campo.rotulo()).isNotBlank());
    }

    private static VersaoDoCenario versao(int numero, ConteudoDoCenario conteudo) {
        return new VersaoDoCenario((long) numero, numero, StatusDaVersao.APROVADA, conteudo, 7L);
    }

    private static ConteudoDoCenario comTitulo(String titulo) {
        var completo = ConteudosDeTeste.completo();
        return new ConteudoDoCenario(titulo, completo.roteiro(), completo.curriculo(), completo.faixaEtaria(),
                completo.duracaoPrevistaMin());
    }

    private static ConteudoDoCenario comRoteiro(RoteiroDoCenario roteiro) {
        var completo = ConteudosDeTeste.completo();
        return new ConteudoDoCenario(completo.titulo(), roteiro, completo.curriculo(), completo.faixaEtaria(),
                completo.duracaoPrevistaMin());
    }
}
