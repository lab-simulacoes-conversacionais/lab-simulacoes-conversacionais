package br.ufg.inf.labsimulacoes.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * O evento da triagem leva só ids, códigos e o momento (RF-24, RNF-01). Se alguém acrescentar um campo de
 * texto, este teste falha e pede a revisão do payload.
 */
class SinalizacaoTriadaTest {

    private static final Instant MOMENTO = Instant.parse("2026-11-24T14:00:00Z");

    @Test
    void payloadTemSoIdsCodigosEOMomento() {
        assertThat(Arrays.stream(SinalizacaoTriada.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("sinalizacaoId", "versaoId", "decisao", "gravidadeFinal", "gravidadePercebida",
                        "perfil", "triadaPorId", "ocorridoEm")
                .doesNotContain("pergunta", "resposta", "justificativa", "trecho", "texto");
    }

    @Test
    void confirmadaLevaAGravidadeFinal() {
        var evento = new SinalizacaoTriada(1, 2, "CONFIRMADA", "GRAVE", "MODERADA", "PROFESSOR", 3, MOMENTO);

        assertThat(evento.confirmada()).isTrue();
        assertThat(evento.gravidadeFinal()).isEqualTo("GRAVE");
    }

    @Test
    void descartadaNaoTemGravidadeFinal() {
        var evento = new SinalizacaoTriada(1, 2, "DESCARTADA", null, "GRAVE", "PROFESSOR", 3, MOMENTO);

        assertThat(evento.confirmada()).isFalse();
        assertThat(evento.gravidadeFinal()).isNull();
    }

    @Test
    void gravidadeFinalIncoerenteComADecisaoERecusada() {
        assertThatThrownBy(() -> new SinalizacaoTriada(1, 2, "CONFIRMADA", null, "GRAVE", "PROFESSOR", 3, MOMENTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SinalizacaoTriada(1, 2, "DESCARTADA", "LEVE", "GRAVE", "PROFESSOR", 3, MOMENTO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codigosEMomentoSaoObrigatorios() {
        assertThatThrownBy(() -> new SinalizacaoTriada(1, 2, null, null, "GRAVE", "PROFESSOR", 3, MOMENTO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoTriada(1, 2, "DESCARTADA", null, null, "PROFESSOR", 3, MOMENTO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoTriada(1, 2, "DESCARTADA", null, "GRAVE", null, 3, MOMENTO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoTriada(1, 2, "DESCARTADA", null, "GRAVE", "PROFESSOR", 3, null))
                .isInstanceOf(NullPointerException.class);
    }
}
