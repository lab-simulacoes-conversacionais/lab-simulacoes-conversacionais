package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Suspensão e reativação do cenário pelas regras de gravidade (RF-25, RN-23), sem banco nem Spring. */
class SuspensaoDoCenarioTest {

    private static final long AUTOR = 7L;
    private static final Revisor CURADORA = new Revisor(20L, true);
    private static final Instant AGORA = Instant.parse("2026-11-27T13:00:00Z");

    @Test
    void aprovadoESuspensoSemMexerNaVersaoVigente() {
        Cenario aprovado = aprovado();

        Cenario suspenso = aprovado.suspender();

        assertThat(suspenso.status()).isEqualTo(StatusDoCenario.SUSPENSO);
        assertThat(suspenso.versao()).isEqualTo(aprovado.versao());
        assertThat(suspenso.autorId()).isEqualTo(AUTOR);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDoCenario.class, names = "APROVADO", mode = EnumSource.Mode.EXCLUDE)
    void soOAprovadoESuspenso(StatusDoCenario status) {
        Cenario cenario = comStatus(aprovado(), status);

        assertThatThrownBy(cenario::suspender)
                .isInstanceOf(SuspensaoNaoPermitida.class)
                .hasMessageContaining(status.name());
    }

    /** RN-23d: reativado, o cenário volta à sala com a mesma versão vigente. */
    @Test
    void suspensoEReativadoComAMesmaVersao() {
        Cenario suspenso = aprovado().suspender();

        Cenario reativado = suspenso.reativar();

        assertThat(reativado.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(reativado.versao()).isEqualTo(suspenso.versao());
    }

    @ParameterizedTest
    @EnumSource(value = StatusDoCenario.class, names = "SUSPENSO", mode = EnumSource.Mode.EXCLUDE)
    void soOSuspensoEReativado(StatusDoCenario status) {
        Cenario cenario = comStatus(aprovado(), status);

        assertThatThrownBy(cenario::reativar)
                .isInstanceOf(ReativacaoNaoPermitida.class)
                .hasMessageContaining(status.name());
    }

    /** RN-04: a marca de sugerido por IA continua no cenário suspenso e no reativado. */
    @Test
    void suspensaoGuardaAMarcaDeSugeridoPorIa() {
        Cenario aprovado = aprovado();
        Cenario sugerido = new Cenario(aprovado.id(), AUTOR, aprovado.status(), aprovado.versao(), null, true, 40L);

        Cenario suspenso = sugerido.suspender();
        Cenario reativado = suspenso.reativar();

        assertThat(suspenso.sugeridoPorIa()).isTrue();
        assertThat(suspenso.sugestaoDaIa()).isEqualTo(40L);
        assertThat(reativado.sugeridoPorIa()).isTrue();
        assertThat(reativado.sugestaoDaIa()).isEqualTo(40L);
    }

    /** A versão nova em curadoria segue na curadoria enquanto o cenário é suspenso e reativado. */
    @Test
    void versaoNovaEmCuradoriaContinuaNaCuradoria() {
        Cenario emCuradoria = aprovado().novaVersao(AUTOR).enviarACuradoria(AUTOR);

        Cenario suspenso = emCuradoria.suspender();

        assertThat(suspenso.versao().status()).isEqualTo(StatusDaVersao.EM_CURADORIA);
        assertThat(suspenso.reativar().versao().status()).isEqualTo(StatusDaVersao.EM_CURADORIA);
    }

    /**
     * O suspenso continua Suspenso enquanto a versão nova vai e volta da curadoria: a vigente segue fora da sala
     * até a nova ser aprovada (RN-08, RN-23b).
     */
    @Test
    void suspensoContinuaSuspensoAoEnviarEAoDevolverAVersaoNova() {
        Cenario suspenso = aprovado().suspender();

        Cenario enviado = suspenso.novaVersao(AUTOR).enviarACuradoria(AUTOR);
        Cenario devolvido = enviado.devolver(CURADORA, "Corrija a data da Proclamação.", AGORA);
        Cenario reenviado = devolvido.enviarACuradoria(AUTOR);

        assertThat(enviado.status()).isEqualTo(StatusDoCenario.SUSPENSO);
        assertThat(devolvido.status()).isEqualTo(StatusDoCenario.SUSPENSO);
        assertThat(devolvido.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(reenviado.status()).isEqualTo(StatusDoCenario.SUSPENSO);
    }

    /** RN-23b: a aprovação da versão nova tira o cenário da suspensão. */
    @Test
    void aprovacaoDaVersaoNovaTiraDaSuspensao() {
        Cenario enviado = aprovado().suspender().novaVersao(AUTOR).enviarACuradoria(AUTOR);

        Cenario aprovado = enviado.aprovar(CURADORA, ConteudosDeTeste.CONFERIDA, AGORA);

        assertThat(aprovado.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(aprovado.versao().numero()).isEqualTo(2);
        assertThat(aprovado.versao().status()).isEqualTo(StatusDaVersao.APROVADA);
    }

    private static Cenario aprovado() {
        var versao = new VersaoDoCenario(2L, 1, StatusDaVersao.RASCUNHO, ConteudosDeTeste.completo(), AUTOR);
        return new Cenario(1L, AUTOR, StatusDoCenario.RASCUNHO, versao).enviarACuradoria(AUTOR)
                .aprovar(CURADORA, ConteudosDeTeste.CONFERIDA, AGORA);
    }

    private static Cenario comStatus(Cenario cenario, StatusDoCenario status) {
        return new Cenario(cenario.id(), cenario.autorId(), status, cenario.versao(), cenario.ultimaDevolucao(),
                cenario.sugeridoPorIa(), cenario.sugestaoDaIa());
    }
}
