package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** O que Cenários guarda de cada sinalização recebida da Auditoria (RF-25). */
class SinalizacaoDaVersaoTest {

    private static final Instant REGISTRO = Instant.parse("2026-11-27T13:00:00Z");
    private static final Instant TRIAGEM = Instant.parse("2026-11-27T14:00:00Z");

    @Test
    void pendenteNaoTemTriagem() {
        var pendente = SinalizacaoDaVersao.pendente(
                1, 10, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, REGISTRO);

        assertThat(pendente.pendente()).isTrue();
        assertThat(pendente.situacao()).isEqualTo(SituacaoDaSinalizacao.PENDENTE);
        assertThat(pendente.gravidadeFinal()).isNull();
        assertThat(pendente.triadaPorId()).isNull();
        assertThat(pendente.triadaEm()).isNull();
    }

    @Test
    void confirmadaTemGravidadeFinalEQuemTriou() {
        var confirmada = SinalizacaoDaVersao.confirmada(
                1, 10, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, GravidadeDoErro.MODERADA, 7, REGISTRO,
                TRIAGEM);

        assertThat(confirmada.pendente()).isFalse();
        assertThat(confirmada.situacao()).isEqualTo(SituacaoDaSinalizacao.CONFIRMADA);
        assertThat(confirmada.gravidadePercebida()).isEqualTo(GravidadeDoErro.GRAVE);
        assertThat(confirmada.gravidadeFinal()).isEqualTo(GravidadeDoErro.MODERADA);
        assertThat(confirmada.triadaPorId()).isEqualTo(7L);
        assertThat(confirmada.triadaEm()).isEqualTo(TRIAGEM);
    }

    @Test
    void descartadaNaoTemGravidadeFinal() {
        var descartada = SinalizacaoDaVersao.descartada(
                1, 10, PerfilDeQuemSinalizou.CURADOR, GravidadeDoErro.LEVE, 7, REGISTRO, TRIAGEM);

        assertThat(descartada.situacao()).isEqualTo(SituacaoDaSinalizacao.DESCARTADA);
        assertThat(descartada.gravidadeFinal()).isNull();
        assertThat(descartada.triadaPorId()).isEqualTo(7L);
    }

    @Test
    void confirmadaPrecisaDaGravidadeFinal() {
        assertThatThrownBy(() -> SinalizacaoDaVersao.confirmada(
                        1, 10, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, null, 7, REGISTRO, TRIAGEM))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoDaVersao(
                        1, 10, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, SituacaoDaSinalizacao.CONFIRMADA,
                        null, 7L, REGISTRO, TRIAGEM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void soAConfirmadaTemGravidadeFinal() {
        assertThatThrownBy(() -> new SinalizacaoDaVersao(
                        1, 10, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, SituacaoDaSinalizacao.DESCARTADA,
                        GravidadeDoErro.LEVE, 7L, REGISTRO, TRIAGEM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SinalizacaoDaVersao(
                        1, 10, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, SituacaoDaSinalizacao.PENDENTE,
                        GravidadeDoErro.LEVE, null, REGISTRO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void soATriadaTemQuemTriouEQuando() {
        assertThatThrownBy(() -> new SinalizacaoDaVersao(
                        1, 10, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, SituacaoDaSinalizacao.PENDENTE,
                        null, 7L, REGISTRO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SinalizacaoDaVersao(
                        1, 10, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, SituacaoDaSinalizacao.PENDENTE,
                        null, null, REGISTRO, TRIAGEM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SinalizacaoDaVersao(
                        1, 10, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, SituacaoDaSinalizacao.DESCARTADA,
                        null, 7L, REGISTRO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void camposObrigatorios() {
        assertThatThrownBy(() -> SinalizacaoDaVersao.pendente(1, 10, null, GravidadeDoErro.GRAVE, REGISTRO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SinalizacaoDaVersao.pendente(1, 10, PerfilDeQuemSinalizou.PROFESSOR, null, REGISTRO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SinalizacaoDaVersao.pendente(
                        1, 10, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoDaVersao(
                        1, 10, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, null, null, null, REGISTRO, null))
                .isInstanceOf(NullPointerException.class);
    }
}
