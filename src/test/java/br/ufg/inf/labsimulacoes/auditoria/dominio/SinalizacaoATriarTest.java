package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** A sinalização é triada uma vez só (RF-24), e a triagem sabe se saiu fora do prazo (RN-24). */
class SinalizacaoATriarTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    /** Sexta-feira: vence na terça às 15h. */
    private static final Instant CRIADA = naEscola("2026-11-27T15:00");

    @Test
    void pendenteAceitaATriagem() {
        assertThatCode(() -> sinalizacao(true).aceitar(Triagem.descartar(1, 9, CRIADA))).doesNotThrowAnyException();
        assertThatCode(() -> sinalizacao(true).aceitar(Triagem.confirmar(1, GravidadeFinal.LEVE, 9, CRIADA)))
                .doesNotThrowAnyException();
    }

    @Test
    void jaTriadaNaoETriadaDeNovo() {
        assertThatThrownBy(() -> sinalizacao(false).aceitar(Triagem.confirmar(1, GravidadeFinal.GRAVE, 9, CRIADA)))
                .isInstanceOf(SinalizacaoJaTriada.class)
                .hasMessage("A sinalização 1 já foi triada");
    }

    @Test
    void triagemDeOutraSinalizacaoERecusada() {
        assertThatThrownBy(() -> sinalizacao(true).aceitar(Triagem.descartar(2, 9, CRIADA)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** RN-24: no próprio limite a triagem ainda está no prazo. */
    @Test
    void triagemDepoisDoLimiteSaiForaDoPrazo() {
        SinalizacaoATriar sinalizacao = sinalizacao(true);

        assertThat(sinalizacao.foraDoPrazo(naEscola("2026-12-01T15:00"), FUSO)).isFalse();
        assertThat(sinalizacao.foraDoPrazo(naEscola("2026-12-01T15:01"), FUSO)).isTrue();
    }

    @Test
    void escolhasEMomentoSaoObrigatorios() {
        assertThatThrownBy(() -> new SinalizacaoATriar(1, true, 3, null, PerfilDaSinalizacao.PROFESSOR, CRIADA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoATriar(1, true, 3, GravidadePercebida.LEVE, null, CRIADA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SinalizacaoATriar(
                        1, true, 3, GravidadePercebida.LEVE, PerfilDaSinalizacao.PROFESSOR, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static SinalizacaoATriar sinalizacao(boolean pendente) {
        return new SinalizacaoATriar(
                1, pendente, 3, GravidadePercebida.MODERADA, PerfilDaSinalizacao.PROFESSOR, CRIADA);
    }

    private static Instant naEscola(String momento) {
        return LocalDateTime.parse(momento).atZone(FUSO).toInstant();
    }
}
