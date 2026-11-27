package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** A pendência sem os textos sabe o próprio prazo e se está em destaque (RN-24). */
class PendenciaDaTriagemTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    /** Sexta-feira: vence na terça às 15h. */
    private static final Instant CRIADA = naEscola("2026-11-27T15:00");

    @Test
    void limiteEDoisDiasUteisDepois() {
        assertThat(pendencia().limiteDaTriagem(FUSO)).isEqualTo(naEscola("2026-12-01T15:00"));
    }

    @Test
    void ficaEmDestaqueSoDepoisDoLimite() {
        assertThat(pendencia().foraDoPrazo(naEscola("2026-12-01T15:00"), FUSO)).isFalse();
        assertThat(pendencia().foraDoPrazo(naEscola("2026-12-01T15:01"), FUSO)).isTrue();
    }

    @Test
    void tipoEMomentoSaoObrigatorios() {
        assertThatThrownBy(() -> new PendenciaDaTriagem(1, null, CRIADA)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PendenciaDaTriagem(1, TipoDeErro.ANACRONISMO, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static PendenciaDaTriagem pendencia() {
        return new PendenciaDaTriagem(1, TipoDeErro.ERRO_HISTORICO, CRIADA);
    }

    private static Instant naEscola(String momento) {
        return LocalDateTime.parse(momento).atZone(FUSO).toInstant();
    }
}
