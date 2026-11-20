package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** RN-04 e RN-05: a sugestão da IA é só um ponto de partida, sem banco nem Spring. */
class CenarioSugeridoPelaIaTest {

    private static final long PROFESSORA = 7L;
    private static final long GERACAO = 42L;
    private static final Revisor CURADORA = new Revisor(20L, true);
    private static final Instant AGORA = Instant.parse("2026-11-14T13:00:00Z");

    @Test
    void sugestaoEntraComoRascunhoNovoMarcadoSugeridoPorIa() {
        Cenario sugerido = Cenario.sugeridoPelaIa(PROFESSORA, ConteudosDeTeste.completo(), GERACAO);

        assertThat(sugerido.id()).isNull();
        assertThat(sugerido.status()).isEqualTo(StatusDoCenario.RASCUNHO);
        assertThat(sugerido.versao().numero()).isEqualTo(1);
        assertThat(sugerido.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(sugerido.versao().aprovacao()).isNull();
        assertThat(sugerido.sugeridoPorIa()).isTrue();
        assertThat(sugerido.sugestaoDaIa()).isEqualTo(GERACAO);
    }

    /** Quem pediu responde pelo rascunho e é quem o ajusta e envia à curadoria. */
    @Test
    void quemPediuEOAutorDoRascunhoEDaVersao() {
        Cenario sugerido = Cenario.sugeridoPelaIa(PROFESSORA, ConteudosDeTeste.completo(), GERACAO);

        assertThat(sugerido.autorId()).isEqualTo(PROFESSORA);
        assertThat(sugerido.versao().criadaPorId()).isEqualTo(PROFESSORA);
    }

    @Test
    void cenarioCriadoDoZeroNaoEhSugeridoPorIa() {
        assertThat(Cenario.novo(PROFESSORA, ConteudosDeTeste.completo()).sugeridoPorIa()).isFalse();
    }

    /** A marca continua depois que o texto é ajustado, para o curador saber que precisa conferir os fatos. */
    @Test
    void marcaContinuaDepoisDeAjustarOEnviarEDevolver() {
        Cenario devolvido = Cenario.sugeridoPelaIa(PROFESSORA, ConteudosDeTeste.soComTitulo(), GERACAO)
                .alterarRascunho(ConteudosDeTeste.completo())
                .enviarACuradoria(PROFESSORA)
                .devolver(CURADORA, "Confira as datas com as fontes", AGORA);

        assertThat(devolvido.sugeridoPorIa()).isTrue();
        assertThat(devolvido.sugestaoDaIa()).isEqualTo(GERACAO);
    }

    /** RN-04: a retenção pode apagar os metadados da geração, mas o cenário continua marcado. */
    @Test
    void marcaNaoDependeDosMetadadosDaGeracao() {
        var semMetadados = new Cenario(1L, PROFESSORA, StatusDoCenario.RASCUNHO,
                VersaoDoCenario.primeira(PROFESSORA, ConteudosDeTeste.soComTitulo()), null, true, null);

        Cenario ajustado = semMetadados.alterarRascunho(ConteudosDeTeste.completo());

        assertThat(ajustado.sugeridoPorIa()).isTrue();
        assertThat(ajustado.sugestaoDaIa()).isNull();
    }

    @Test
    void metadadosDeGeracaoSemAMarcaSaoRecusados() {
        VersaoDoCenario versao = VersaoDoCenario.primeira(PROFESSORA, ConteudosDeTeste.completo());

        assertThatThrownBy(() -> new Cenario(null, PROFESSORA, StatusDoCenario.RASCUNHO, versao, null, false, GERACAO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sugerido por IA");
    }

    /** RN-05: quem aprova é o curador, que assume a responsabilidade pedagógica. */
    @Test
    void curadorAprovaOSugeridoEAMarcaFicaNoCenarioAprovado() {
        Cenario aprovado = Cenario.sugeridoPelaIa(PROFESSORA, ConteudosDeTeste.completo(), GERACAO)
                .enviarACuradoria(PROFESSORA)
                .aprovar(CURADORA, ConteudosDeTeste.CONFERIDA, AGORA);

        assertThat(aprovado.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(aprovado.versao().aprovacao().curadorId()).isEqualTo(CURADORA.id());
        assertThat(aprovado.sugeridoPorIa()).isTrue();
    }

    /** RN-04 e RN-36: o curador que pediu a sugestão é o autor e não aprova o próprio rascunho. */
    @Test
    void curadorQuePediuASugestaoNaoAprova() {
        Cenario emCuradoria = Cenario.sugeridoPelaIa(CURADORA.id(), ConteudosDeTeste.completo(), GERACAO)
                .enviarACuradoria(CURADORA.id());

        assertThatThrownBy(() -> emCuradoria.aprovar(CURADORA, ConteudosDeTeste.CONFERIDA, AGORA))
                .isInstanceOf(DecisaoDeCuradoriaNaoPermitida.class)
                .hasMessageContaining("RN-36");
    }

    /** RN-02 e RN-03 valem também para o sugerido: a IA pode deixar campos vazios. */
    @Test
    void sugestaoIncompletaNaoVaiParaACuradoriaSemAjuste() {
        Cenario sugerido = Cenario.sugeridoPelaIa(PROFESSORA, ConteudosDeTeste.soComTitulo(), GERACAO);

        assertThatThrownBy(() -> sugerido.enviarACuradoria(PROFESSORA)).isInstanceOf(CenarioIncompleto.class);
    }
}
