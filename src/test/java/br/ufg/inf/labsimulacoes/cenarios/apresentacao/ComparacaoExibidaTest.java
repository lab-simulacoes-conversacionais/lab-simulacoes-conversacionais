package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.cenarios.apresentacao.ComparacaoExibida.Campo;
import br.ufg.inf.labsimulacoes.cenarios.apresentacao.ComparacaoExibida.Valor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CampoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ComparacaoDeVersoes;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.DiferencaDoCampo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RoteiroDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoDoCenario;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Critério de aceite da #34: as diferenças entre versões vêm destacadas para a tela (RF-11), sem Spring. */
class ComparacaoExibidaTest {

    private static final RoteiroDoCenario ROTEIRO = ConteudosDeTeste.ROTEIRO;

    @Test
    void separaOsCamposQueMudaramDosIguaisNaOrdemDoEditor() {
        var depois = comTitulo(comObjetivos(List.of("Explicar o voo", "Comparar inventos")), "Outro título");

        var exibida = comparar(ConteudosDeTeste.completo(), depois);

        assertThat(exibida.antiga()).isEqualTo(1);
        assertThat(exibida.nova()).isEqualTo(2);
        assertThat(exibida.semDiferencas()).isFalse();
        assertThat(exibida.mudaram()).extracting(Campo::rotulo).containsExactly("Título", "Objetivos de aprendizagem");
        assertThat(exibida.iguais()).extracting(Campo::rotulo).contains("Personagem", "Fontes").doesNotContain("Título");
    }

    @Test
    void destacaSoOQueSaiuDeUmLadoEOQueEntrouDoOutro() {
        var antes = comObjetivos(List.of("Explicar o voo", "Situar Paris em 1906"));
        var depois = comObjetivos(List.of("Explicar o voo", "Comparar inventos"));

        Campo objetivos = comparar(antes, depois).mudaram().getFirst();

        assertThat(objetivos.antes()).containsExactly(
                new Valor("Explicar o voo", false), new Valor("Situar Paris em 1906", true));
        assertThat(objetivos.depois()).containsExactly(
                new Valor("Explicar o voo", false), new Valor("Comparar inventos", true));
        assertThat(objetivos.soMudouAOrdem()).isFalse();
    }

    /**
     * Como no domínio: de ["A", "A"] para ["A"], o segundo "A" é o que saiu. O roteiro já tira itens
     * repetidos, então a diferença é montada direto, como o domínio a devolveria.
     */
    @Test
    void contaAsRepeticoes() {
        Campo objetivos = Campo.de(new DiferencaDoCampo(
                CampoDoCenario.FONTES, List.of("A", "A"), List.of("A"), List.of("A"), List.of()));

        assertThat(objetivos.antes()).containsExactly(new Valor("A", false), new Valor("A", true));
        assertThat(objetivos.depois()).containsExactly(new Valor("A", false));
    }

    @Test
    void soAOrdemMudouNaoDestacaNadaMasAvisa() {
        Campo objetivos = comparar(comObjetivos(List.of("A", "B")), comObjetivos(List.of("B", "A"))).mudaram().getFirst();

        assertThat(objetivos.soMudouAOrdem()).isTrue();
        assertThat(objetivos.antes()).noneMatch(Valor::destacado);
        assertThat(objetivos.depois()).noneMatch(Valor::destacado);
    }

    @Test
    void versoesIguaisNaoTemDiferenca() {
        var exibida = comparar(ConteudosDeTeste.completo(), ConteudosDeTeste.completo());

        assertThat(exibida.semDiferencas()).isTrue();
        assertThat(exibida.iguais()).hasSize(14);
    }

    private static ComparacaoExibida comparar(ConteudoDoCenario antes, ConteudoDoCenario depois) {
        return ComparacaoExibida.de(ComparacaoDeVersoes.entre(versao(1, antes), versao(2, depois)));
    }

    private static VersaoDoCenario versao(int numero, ConteudoDoCenario conteudo) {
        return new VersaoDoCenario((long) numero, numero, StatusDaVersao.APROVADA, conteudo, 7L);
    }

    private static ConteudoDoCenario comObjetivos(List<String> objetivos) {
        var roteiro = new RoteiroDoCenario(ROTEIRO.personagem(), ROTEIRO.papelDaIa(), ROTEIRO.contexto(), objetivos,
                ROTEIRO.limitesDeAssunto(), ROTEIRO.fontes(), ROTEIRO.perguntasDisparadoras());
        var completo = ConteudosDeTeste.completo();
        return new ConteudoDoCenario(completo.titulo(), roteiro, completo.curriculo(), completo.faixaEtaria(),
                completo.duracaoPrevistaMin());
    }

    private static ConteudoDoCenario comTitulo(ConteudoDoCenario conteudo, String titulo) {
        return new ConteudoDoCenario(titulo, conteudo.roteiro(), conteudo.curriculo(), conteudo.faixaEtaria(),
                conteudo.duracaoPrevistaMin());
    }
}
