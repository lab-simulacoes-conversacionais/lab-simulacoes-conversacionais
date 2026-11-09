package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste.CURRICULO;
import static br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste.ROTEIRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConteudoDoCenarioTest {

    private static final String TITULO = "O voo do 14-bis";
    private static final String FAIXA = "13 a 14 anos";

    @Test
    void conteudoCompletoNaoTemPendencias() {
        assertThat(ConteudosDeTeste.completo().pendencias()).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void tituloEObrigatorioDesdeORascunho(String vazio) {
        assertThatThrownBy(() -> new ConteudoDoCenario(vazio, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("título");
    }

    @Test
    void rascunhoSoComTituloTemTodasAsPendencias() {
        assertThat(ConteudosDeTeste.soComTitulo().pendencias())
                .containsExactlyInAnyOrder(PendenciaDoCenario.values());
    }

    /** Cada campo do RN-02 e do RN-03, retirado sozinho, precisa virar a sua pendência. */
    @ParameterizedTest
    @MethodSource("conteudosSemUmCampo")
    void cadaCampoObrigatorioViraUmaPendencia(ConteudoDoCenario conteudo, PendenciaDoCenario esperada) {
        assertThat(conteudo.pendencias()).containsExactly(esperada);
    }

    static Stream<Arguments> conteudosSemUmCampo() {
        var r = ROTEIRO;
        var c = CURRICULO;
        return Stream.of(
                semCurriculo(new VinculoCurricular(null, c.etapa(), c.ano(), c.habilidades()), PendenciaDoCenario.COMPONENTE),
                semCurriculo(new VinculoCurricular(c.componente(), null, c.ano(), c.habilidades()), PendenciaDoCenario.ETAPA),
                semCurriculo(new VinculoCurricular(c.componente(), c.etapa(), null, c.habilidades()), PendenciaDoCenario.ANO),
                semCurriculo(new VinculoCurricular(c.componente(), c.etapa(), c.ano(), List.of()), PendenciaDoCenario.HABILIDADE_BNCC),
                semRoteiro(new RoteiroDoCenario(null, r.papelDaIa(), r.contexto(), r.objetivos(), r.limitesDeAssunto(), r.fontes(), null), PendenciaDoCenario.PERSONAGEM),
                semRoteiro(new RoteiroDoCenario(r.personagem(), " ", r.contexto(), r.objetivos(), r.limitesDeAssunto(), r.fontes(), null), PendenciaDoCenario.PAPEL_DA_IA),
                semRoteiro(new RoteiroDoCenario(r.personagem(), r.papelDaIa(), null, r.objetivos(), r.limitesDeAssunto(), r.fontes(), null), PendenciaDoCenario.CONTEXTO),
                semRoteiro(new RoteiroDoCenario(r.personagem(), r.papelDaIa(), r.contexto(), List.of(), r.limitesDeAssunto(), r.fontes(), null), PendenciaDoCenario.OBJETIVO),
                semRoteiro(new RoteiroDoCenario(r.personagem(), r.papelDaIa(), r.contexto(), r.objetivos(), null, r.fontes(), null), PendenciaDoCenario.LIMITES_DE_ASSUNTO),
                semRoteiro(new RoteiroDoCenario(r.personagem(), r.papelDaIa(), r.contexto(), r.objetivos(), r.limitesDeAssunto(), List.of(), null), PendenciaDoCenario.FONTE),
                Arguments.of(new ConteudoDoCenario(TITULO, r, c, " ", 20), PendenciaDoCenario.FAIXA_ETARIA),
                Arguments.of(new ConteudoDoCenario(TITULO, r, c, FAIXA, null), PendenciaDoCenario.DURACAO_PREVISTA));
    }

    private static Arguments semCurriculo(VinculoCurricular curriculo, PendenciaDoCenario pendencia) {
        return Arguments.of(new ConteudoDoCenario(TITULO, ROTEIRO, curriculo, FAIXA, 20), pendencia);
    }

    private static Arguments semRoteiro(RoteiroDoCenario roteiro, PendenciaDoCenario pendencia) {
        return Arguments.of(new ConteudoDoCenario(TITULO, roteiro, CURRICULO, FAIXA, 20), pendencia);
    }

    @Test
    void itemEmBrancoNaoContaComoObjetivoNemFonte() {
        var roteiro = new RoteiroDoCenario("P", "Papel", "Contexto",
                Arrays.asList(" ", null), List.of("Limite"), List.of("  "), null);

        assertThat(new ConteudoDoCenario(TITULO, roteiro, CURRICULO, FAIXA, 20).pendencias())
                .containsExactly(PendenciaDoCenario.OBJETIVO, PendenciaDoCenario.FONTE);
    }

    @Test
    void perguntasDisparadorasSaoOpcionais() {
        var r = ROTEIRO;
        var roteiro = new RoteiroDoCenario(r.personagem(), r.papelDaIa(), r.contexto(), r.objetivos(),
                r.limitesDeAssunto(), r.fontes(), null);

        assertThat(new ConteudoDoCenario(TITULO, roteiro, CURRICULO, FAIXA, 20).pendencias()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 20, 30})
    void duracaoEntre10E30MinutosEAceita(int minutos) {
        assertThat(new ConteudoDoCenario(TITULO, null, null, null, minutos).duracaoPrevistaMin())
                .isEqualTo(minutos);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 9, 31, 50})
    void duracaoForaDe10A30MinutosERecusada(int minutos) {
        assertThatThrownBy(() -> new ConteudoDoCenario(TITULO, null, null, null, minutos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 e 30");
    }

    @Test
    void textosFicamSemEspacosNasPontasEListasSemRepeticao() {
        var roteiro = new RoteiroDoCenario(" Santos Dumont ", " Papel ", " Contexto ",
                List.of(" Objetivo ", "Objetivo"), null, null, null);

        var conteudo = new ConteudoDoCenario(" Título ", roteiro, null, " 13 anos ", null);

        assertThat(conteudo.titulo()).isEqualTo("Título");
        assertThat(conteudo.faixaEtaria()).isEqualTo("13 anos");
        assertThat(conteudo.roteiro().personagem()).isEqualTo("Santos Dumont");
        assertThat(conteudo.roteiro().objetivos()).containsExactly("Objetivo");
    }

    @Test
    void habilidadesFicamEmMaiusculasOrdenadasESemRepeticao() {
        var curriculo = new VinculoCurricular("História", "ef", 8, List.of("ef08hi03", " EF08HI01 ", "EF08HI03"));

        assertThat(curriculo.etapa()).isEqualTo("EF");
        assertThat(curriculo.habilidades()).containsExactly("EF08HI01", "EF08HI03");
    }

    @ParameterizedTest
    @ValueSource(strings = {"E", "EFF", "8º", "Ensino Fundamental"})
    void etapaForaDoFormatoDoCatalogoERecusada(String etapa) {
        assertThatThrownBy(() -> new VinculoCurricular("História", etapa, 8, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("etapa");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, -8})
    void anoForaDe1A9ERecusado(int ano) {
        assertThatThrownBy(() -> new VinculoCurricular("História", "EF", ano, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 e 9");
    }
}
