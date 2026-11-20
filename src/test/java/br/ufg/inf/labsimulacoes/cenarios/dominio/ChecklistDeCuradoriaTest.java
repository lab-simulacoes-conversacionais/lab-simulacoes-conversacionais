package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Checklist de curadoria e teste antes de aprovar (RF-10, RN-06), sem banco nem Spring. */
class ChecklistDeCuradoriaTest {

    private static final long AUTOR = 7L;
    private static final Revisor CURADORA = new Revisor(20L, true);
    private static final Instant AGORA = Instant.parse("2026-11-20T13:00:00Z");
    private static final Set<ItemDoChecklist> TODOS = Set.of(ItemDoChecklist.values());

    @Test
    void checklistInteiroECincoPerguntasAprovam() {
        Cenario aprovado = emCuradoria().aprovar(CURADORA, new ConferenciaDaCuradoria(TODOS, 5), AGORA);

        assertThat(aprovado.status()).isEqualTo(StatusDoCenario.APROVADO);
    }

    @Test
    void maisPerguntasQueOMinimoTambemAprovam() {
        assertThat(new ConferenciaDaCuradoria(TODOS, 12).completa()).isTrue();
    }

    /** RN-06: cada item do checklist é obrigatório. */
    @ParameterizedTest
    @EnumSource(ItemDoChecklist.class)
    void faltandoUmItemNaoAprova(ItemDoChecklist naoMarcado) {
        var marcados = EnumSet.allOf(ItemDoChecklist.class);
        marcados.remove(naoMarcado);
        var conferencia = new ConferenciaDaCuradoria(marcados, 5);

        assertThatThrownBy(() -> emCuradoria().aprovar(CURADORA, conferencia, AGORA))
                .isInstanceOf(AprovacaoIncompleta.class)
                .hasMessageContaining(naoMarcado.descricao())
                .hasMessageContaining("RN-06");
    }

    /** RN-06: pelo menos 5 perguntas de teste. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4})
    void menosDeCincoPerguntasNaoAprova(int perguntas) {
        var conferencia = new ConferenciaDaCuradoria(TODOS, perguntas);

        assertThatThrownBy(() -> emCuradoria().aprovar(CURADORA, conferencia, AGORA))
                .isInstanceOf(AprovacaoIncompleta.class)
                .hasMessageContaining("pelo menos 5 perguntas (feitas: " + perguntas + ")")
                .satisfies(erro -> assertThat(((AprovacaoIncompleta) erro).perguntasFaltando()).isEqualTo(5 - perguntas));
    }

    /** Tudo o que falta vem de uma vez, na ordem do checklist. */
    @Test
    void recusaDizTudoOQueFalta() {
        var conferencia = new ConferenciaDaCuradoria(Set.of(ItemDoChecklist.LINGUAGEM_ADEQUADA), 2);

        assertThatThrownBy(() -> emCuradoria().aprovar(CURADORA, conferencia, AGORA))
                .isInstanceOfSatisfying(AprovacaoIncompleta.class, erro -> {
                    assertThat(erro.itensFaltando()).containsExactly(ItemDoChecklist.FATOS_CONFERIDOS,
                            ItemDoChecklist.OBJETIVOS_COERENTES, ItemDoChecklist.LIMITES_RESPEITADOS);
                    assertThat(erro.perguntasFaltando()).isEqualTo(3);
                    assertThat(erro.getMessage()).isEqualTo("Para aprovar, falta: fatos conferidos com as fontes; "
                            + "objetivos coerentes com a BNCC; limites de assunto respeitados no teste; "
                            + "testar com pelo menos 5 perguntas (feitas: 2) (RN-06)");
                });
    }

    /** As regras de quem aprova vêm antes: o autor é recusado mesmo com tudo conferido (RN-36). */
    @Test
    void autorNaoAprovaMesmoComTudoConferido() {
        assertThatThrownBy(() -> emCuradoria().aprovar(new Revisor(AUTOR, true), ConteudosDeTeste.CONFERIDA, AGORA))
                .isInstanceOf(DecisaoDeCuradoriaNaoPermitida.class);
    }

    @Test
    void conferenciaVaziaNaoTemNadaMarcado() {
        var vazia = ConferenciaDaCuradoria.de(List.of(), 0);

        assertThat(vazia.itensFaltando()).containsExactly(ItemDoChecklist.values());
        assertThat(vazia.perguntasFaltando()).isEqualTo(5);
        assertThat(vazia.completa()).isFalse();
    }

    @Test
    void numeroDePerguntasNegativoERecusado() {
        assertThatThrownBy(() -> new ConferenciaDaCuradoria(TODOS, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cadaItemTemDescricaoParaATela() {
        assertThat(ItemDoChecklist.FATOS_CONFERIDOS.descricao()).isEqualTo("fatos conferidos com as fontes");
    }

    private static Cenario emCuradoria() {
        var versao = new VersaoDoCenario(2L, 1, StatusDaVersao.RASCUNHO, ConteudosDeTeste.completo(), AUTOR);
        return new Cenario(1L, AUTOR, StatusDoCenario.RASCUNHO, versao).enviarACuradoria(AUTOR);
    }
}
