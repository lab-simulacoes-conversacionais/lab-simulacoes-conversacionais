package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Envio, aprovação e devolução (RF-08), sem banco nem Spring. */
class CuradoriaDoCenarioTest {

    private static final long AUTOR = 7L;
    private static final Revisor CURADORA = new Revisor(20L, true);
    private static final Revisor PROFESSORA = new Revisor(21L, false);
    private static final Instant AGORA = Instant.parse("2026-11-11T13:00:00Z");

    @Test
    void autorEnviaOCenarioCompleto() {
        Cenario enviado = rascunho().enviarACuradoria(AUTOR);

        assertThat(enviado.status()).isEqualTo(StatusDoCenario.EM_CURADORIA);
        assertThat(enviado.versao().status()).isEqualTo(StatusDaVersao.EM_CURADORIA);
    }

    @Test
    void quemNaoEAutorNaoEnvia() {
        assertThatThrownBy(() -> rascunho().enviarACuradoria(CURADORA.id()))
                .isInstanceOf(EnvioNaoPermitido.class)
                .hasMessageContaining("Só o autor");
    }

    @Test
    void cenarioJaEmCuradoriaNaoEEnviadoDeNovo() {
        assertThatThrownBy(() -> emCuradoria().enviarACuradoria(AUTOR)).isInstanceOf(CenarioForaDeRascunho.class);
    }

    @Test
    void curadorAprovaEAVersaoFicaAprovadaComQuemEQuando() {
        Cenario aprovado = emCuradoria().aprovar(CURADORA, AGORA);

        assertThat(aprovado.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(aprovado.versao().status()).isEqualTo(StatusDaVersao.APROVADA);
        assertThat(aprovado.versao().aprovacao()).isEqualTo(new Aprovacao(CURADORA.id(), AGORA));
        assertThat(aprovado.versao().conteudo()).isEqualTo(ConteudosDeTeste.completo());
        assertThat(aprovado.versao().numero()).isEqualTo(1);
    }

    /** RN-05. */
    @Test
    void professorNaoAprova() {
        assertThatThrownBy(() -> emCuradoria().aprovar(PROFESSORA, AGORA))
                .isInstanceOf(DecisaoDeCuradoriaNaoPermitida.class)
                .hasMessageContaining("RN-05");
    }

    /** RN-36: quem tem os dois perfis não aprova o cenário que criou como professor. */
    @Test
    void curadorNaoAprovaOCenarioQueCriou() {
        var autoraCuradora = new Revisor(AUTOR, true);

        assertThatThrownBy(() -> emCuradoria().aprovar(autoraCuradora, AGORA))
                .isInstanceOf(DecisaoDeCuradoriaNaoPermitida.class)
                .hasMessageContaining("RN-36");
    }

    /** RN-36 vale também para quem criou a versão, se não for o autor do cenário. */
    @Test
    void curadorNaoAprovaAVersaoQueCriou() {
        var versao = new VersaoDoCenario(2L, 1, StatusDaVersao.EM_CURADORIA, ConteudosDeTeste.completo(), CURADORA.id());
        var cenario = new Cenario(1L, AUTOR, StatusDoCenario.EM_CURADORIA, versao);

        assertThatThrownBy(() -> cenario.aprovar(CURADORA, AGORA)).isInstanceOf(DecisaoDeCuradoriaNaoPermitida.class);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDaVersao.class, names = {"RASCUNHO", "APROVADA"})
    void soAprovaOuDevolveEmCuradoria(StatusDaVersao status) {
        var versao = new VersaoDoCenario(2L, 1, status, ConteudosDeTeste.completo(), AUTOR);
        var cenario = new Cenario(1L, AUTOR, StatusDoCenario.RASCUNHO, versao);

        assertThatThrownBy(() -> cenario.aprovar(CURADORA, AGORA))
                .isInstanceOf(CenarioForaDeCuradoria.class)
                .hasMessageContaining(status.name());
        assertThatThrownBy(() -> cenario.devolver(CURADORA, "Corrigir", AGORA))
                .isInstanceOf(CenarioForaDeCuradoria.class);
    }

    @Test
    void aprovadoNaoEAprovadoDeNovo() {
        Cenario aprovado = emCuradoria().aprovar(CURADORA, AGORA);

        assertThatThrownBy(() -> aprovado.aprovar(new Revisor(30L, true), AGORA)).isInstanceOf(CenarioForaDeCuradoria.class);
    }

    @Test
    void devolucaoVoltaARascunhoEGuardaOComentario() {
        Cenario devolvido = emCuradoria().devolver(CURADORA, "  Conferir a data do voo com as fontes.  ", AGORA);

        assertThat(devolvido.status()).isEqualTo(StatusDoCenario.RASCUNHO);
        assertThat(devolvido.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(devolvido.versao().numero()).isEqualTo(1);
        assertThat(devolvido.ultimaDevolucao())
                .isEqualTo(new Devolucao(null, CURADORA.id(), "Conferir a data do voo com as fontes.", AGORA));
    }

    /** Depois da devolução o autor corrige e envia de novo. */
    @Test
    void devolvidoPodeSerAlteradoEEnviadoDeNovo() {
        Cenario devolvido = emCuradoria().devolver(CURADORA, "Corrigir", AGORA);

        Cenario reenviado = devolvido.alterarRascunho(ConteudosDeTeste.completo()).enviarACuradoria(AUTOR);

        assertThat(reenviado.status()).isEqualTo(StatusDoCenario.EM_CURADORIA);
        assertThat(reenviado.ultimaDevolucao().comentario()).isEqualTo("Corrigir");
    }

    /** RN-07. */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t"})
    void devolucaoSemComentarioERecusada(String comentario) {
        assertThatThrownBy(() -> emCuradoria().devolver(CURADORA, comentario, AGORA))
                .isInstanceOf(ComentarioObrigatorio.class)
                .hasMessageContaining("RN-07");
    }

    /** RN-05: devolver também é decisão do curador. */
    @Test
    void professorNaoDevolve() {
        assertThatThrownBy(() -> emCuradoria().devolver(PROFESSORA, "Corrigir", AGORA))
                .isInstanceOf(DecisaoDeCuradoriaNaoPermitida.class);
    }

    @Test
    void devolucaoGravadaGanhaIdentificador() {
        var devolucao = new Devolucao(null, CURADORA.id(), "Corrigir", AGORA);

        assertThat(devolucao.comId(5L)).isEqualTo(new Devolucao(5L, CURADORA.id(), "Corrigir", AGORA));
    }

    private static Cenario rascunho() {
        var versao = new VersaoDoCenario(2L, 1, StatusDaVersao.RASCUNHO, ConteudosDeTeste.completo(), AUTOR);
        return new Cenario(1L, AUTOR, StatusDoCenario.RASCUNHO, versao);
    }

    private static Cenario emCuradoria() {
        return rascunho().enviarACuradoria(AUTOR);
    }
}
