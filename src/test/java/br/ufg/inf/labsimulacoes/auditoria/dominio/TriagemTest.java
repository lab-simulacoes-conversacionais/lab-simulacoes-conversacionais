package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** A decisão da triagem (RF-24, RN-22): a confirmada leva a gravidade final, a descartada não. */
class TriagemTest {

    private static final Instant MOMENTO = Instant.parse("2026-11-24T14:00:00Z");

    @ParameterizedTest
    @EnumSource(GravidadeFinal.class)
    void confirmadaLevaQualquerUmaDasTresGravidades(GravidadeFinal gravidade) {
        Triagem triagem = Triagem.confirmar(7, gravidade, 3, MOMENTO);

        assertThat(triagem.decisao()).isEqualTo(DecisaoDaTriagem.CONFIRMADA);
        assertThat(triagem.gravidade()).isEqualTo(gravidade);
        assertThat(triagem.confirmada()).isTrue();
        assertThat(triagem.sinalizacaoId()).isEqualTo(7);
        assertThat(triagem.triadaPorId()).isEqualTo(3);
        assertThat(triagem.triadaEm()).isEqualTo(MOMENTO);
    }

    @Test
    void confirmadaSemGravidadeERecusada() {
        assertThatThrownBy(() -> Triagem.confirmar(7, null, 3, MOMENTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escolha a gravidade final do erro confirmado");
    }

    @Test
    void descartadaNaoTemGravidade() {
        Triagem triagem = Triagem.descartar(7, 3, MOMENTO);

        assertThat(triagem.decisao()).isEqualTo(DecisaoDaTriagem.DESCARTADA);
        assertThat(triagem.gravidade()).isNull();
        assertThat(triagem.confirmada()).isFalse();
    }

    @Test
    void descartadaComGravidadeERecusada() {
        assertThatThrownBy(() -> new Triagem(7, DecisaoDaTriagem.DESCARTADA, GravidadeFinal.GRAVE, 3, MOMENTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A sinalização descartada não tem gravidade final");
    }

    @Test
    void decisaoEMomentoSaoObrigatorios() {
        assertThatThrownBy(() -> new Triagem(7, null, null, 3, MOMENTO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Triagem.descartar(7, 3, null)).isInstanceOf(NullPointerException.class);
    }

    /** Os níveis do curador são os mesmos que quem sinaliza escolhe (RN-21, RN-22). */
    @Test
    void niveisSaoOsMesmosDaGravidadePercebida() {
        assertThat(GravidadeFinal.values())
                .extracting(Enum::name)
                .containsExactly("LEVE", "MODERADA", "GRAVE");
        assertThat(GravidadeFinal.GRAVE.rotulo()).isEqualTo("Grave");
        assertThat(GravidadeFinal.MODERADA.descricao()).isEqualTo("Erro que pode levar a uma compreensão incorreta");
    }
}
