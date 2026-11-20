package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Arquivamento (RF-13, RN-09), sem banco nem Spring. */
class ArquivamentoDoCenarioTest {

    private static final long AUTOR = 7L;
    private static final Revisor CURADORA = new Revisor(20L, true);
    private static final Revisor PROFESSORA = new Revisor(21L, false);

    @ParameterizedTest
    @EnumSource(value = StatusDoCenario.class, names = {"APROVADO", "SUSPENSO"})
    void curadorArquivaOAprovadoEOSuspensoSemMexerNaVersao(StatusDoCenario status) {
        Cenario cenario = comVersaoAprovada(status);

        Cenario arquivado = cenario.arquivar(CURADORA);

        assertThat(arquivado.status()).isEqualTo(StatusDoCenario.ARQUIVADO);
        assertThat(arquivado.versao()).isEqualTo(cenario.versao());
        assertThat(arquivado.autorId()).isEqualTo(AUTOR);
    }

    /** O que nunca chegou à biblioteca não sai dela, e o arquivado não é arquivado de novo. */
    @ParameterizedTest
    @EnumSource(value = StatusDoCenario.class, names = {"RASCUNHO", "EM_CURADORIA", "ARQUIVADO"})
    void soArquivaAprovadoOuSuspenso(StatusDoCenario status) {
        var versao = new VersaoDoCenario(2L, 1, StatusDaVersao.RASCUNHO, ConteudosDeTeste.completo(), AUTOR);
        var cenario = new Cenario(1L, AUTOR, status, versao);

        assertThatThrownBy(() -> cenario.arquivar(CURADORA))
                .isInstanceOf(ArquivamentoNaoPermitido.class)
                .hasMessageContaining(status.name());
    }

    /** RN-04: a marca de sugerido por IA continua no cenário arquivado. */
    @Test
    void arquivadoGuardaAMarcaDeSugeridoPorIa() {
        Cenario sugerido = comVersaoAprovada(StatusDoCenario.APROVADO);
        sugerido = new Cenario(sugerido.id(), AUTOR, sugerido.status(), sugerido.versao(), null, true, 40L);

        Cenario arquivado = sugerido.arquivar(CURADORA);

        assertThat(arquivado.sugeridoPorIa()).isTrue();
        assertThat(arquivado.sugestaoDaIa()).isEqualTo(40L);
    }

    @Test
    void professorNaoArquiva() {
        assertThatThrownBy(() -> comVersaoAprovada(StatusDoCenario.APROVADO).arquivar(PROFESSORA))
                .isInstanceOf(DecisaoDeCuradoriaNaoPermitida.class)
                .hasMessageContaining("Só o curador arquiva");
    }

    /** O RN-36 vale para aprovar, que põe o cenário na sala; arquivar só o tira de lá. */
    @Test
    void curadorArquivaOCenarioQueCriou() {
        var autoraCuradora = new Revisor(AUTOR, true);

        assertThat(comVersaoAprovada(StatusDoCenario.APROVADO).arquivar(autoraCuradora).status())
                .isEqualTo(StatusDoCenario.ARQUIVADO);
    }

    private static Cenario comVersaoAprovada(StatusDoCenario status) {
        var aprovacao = new Aprovacao(CURADORA.id(), Instant.parse("2026-11-11T13:00:00Z"));
        var versao = new VersaoDoCenario(
                2L, 1, StatusDaVersao.APROVADA, ConteudosDeTeste.completo(), AUTOR, aprovacao);
        return new Cenario(1L, AUTOR, status, versao);
    }
}
