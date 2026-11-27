package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** A sinalização pendente sabe o próprio prazo de triagem e se está em destaque (RN-24). */
class SinalizacaoPendenteTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    /** Sexta-feira. */
    private static final Instant CRIADA = naEscola("2026-11-27T15:00");

    @Test
    void limiteEDoisDiasUteisDepois() {
        assertThat(pendente(CRIADA).limiteDaTriagem(FUSO)).isEqualTo(naEscola("2026-12-01T15:00"));
    }

    @Test
    void ficaEmDestaqueSoDepoisDoLimite() {
        SinalizacaoPendente pendente = pendente(CRIADA);

        assertThat(pendente.foraDoPrazo(naEscola("2026-12-01T15:00"), FUSO)).isFalse();
        assertThat(pendente.foraDoPrazo(naEscola("2026-12-01T15:01"), FUSO)).isTrue();
    }

    @Test
    void textosEEscolhasSaoObrigatorios() {
        assertThatThrownBy(() -> new SinalizacaoPendente(1, 2L, 3, null, "r", TipoDeErro.ANACRONISMO,
                        GravidadePercebida.LEVE, "j", PerfilDaSinalizacao.CURADOR, 4, CRIADA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoPendente(1, 2L, 3, "p", "r", null,
                        GravidadePercebida.LEVE, "j", PerfilDaSinalizacao.CURADOR, 4, CRIADA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoPendente(1, 2L, 3, "p", "r", TipoDeErro.ANACRONISMO,
                        GravidadePercebida.LEVE, "j", PerfilDaSinalizacao.CURADOR, 4, null))
                .isInstanceOf(NullPointerException.class);
    }

    /** Depois da retenção a sessão some, e a sinalização continua na fila. */
    @Test
    void semSessaoContinuaNaFila() {
        var pendente = new SinalizacaoPendente(1, null, 3, "p", "r", TipoDeErro.ANACRONISMO,
                GravidadePercebida.LEVE, "j", PerfilDaSinalizacao.CURADOR, 4, CRIADA);

        assertThat(pendente.sessaoId()).isNull();
    }

    private static SinalizacaoPendente pendente(Instant criadaEm) {
        return new SinalizacaoPendente(1, 2L, 3, "Pergunta inventada", "Resposta inventada",
                TipoDeErro.ERRO_HISTORICO, GravidadePercebida.MODERADA, "Justificativa inventada.",
                PerfilDaSinalizacao.PROFESSOR, 4, criadaEm);
    }

    private static Instant naEscola(String momento) {
        return LocalDateTime.parse(momento).atZone(FUSO).toInstant();
    }
}
