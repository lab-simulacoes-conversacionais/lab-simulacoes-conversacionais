package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Versão nova de um cenário com versão vigente e restauração de versão anterior (RF-11, RN-08). */
class NovaVersaoDoCenarioTest {

    private static final long AUTOR = 7L;
    private static final Revisor CURADORA = new Revisor(20L, true);
    private static final Instant AGORA = Instant.parse("2026-11-19T13:00:00Z");

    @Test
    void autorCriaAVersaoSeguinteEmRascunhoComOConteudoDaVigente() {
        Cenario aprovado = aprovado();

        Cenario comNova = aprovado.novaVersao(AUTOR);

        assertThat(comNova.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(comNova.versao().id()).isNull();
        assertThat(comNova.versao().numero()).isEqualTo(2);
        assertThat(comNova.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(comNova.versao().conteudo()).isEqualTo(aprovado.versao().conteudo());
        assertThat(comNova.versao().criadaPorId()).isEqualTo(AUTOR);
        assertThat(comNova.versao().aprovacao()).isNull();
        assertThat(comNova.ultimaDevolucao()).isNull();
    }

    /** RN-23b: o suspenso volta à sala com uma versão nova aprovada. */
    @Test
    void suspensoTambemGanhaVersaoNovaEVoltaAAprovadoQuandoEleEAprovada() {
        Cenario suspenso = comStatus(aprovado(), StatusDoCenario.SUSPENSO);

        Cenario corrigido = suspenso.novaVersao(AUTOR).enviarACuradoria(AUTOR);

        assertThat(corrigido.status()).isEqualTo(StatusDoCenario.SUSPENSO);
        assertThat(corrigido.aprovar(CURADORA, AGORA).status()).isEqualTo(StatusDoCenario.APROVADO);
    }

    /** RN-08: o cenário segue Aprovado enquanto a versão nova vai e volta da curadoria. */
    @Test
    void cenarioContinuaAprovadoDuranteACuradoriaDaVersaoNova() {
        var outroConteudo = new ConteudoDoCenario("O voo do 14-bis, revisto", ConteudosDeTeste.ROTEIRO,
                ConteudosDeTeste.CURRICULO, "13 a 14 anos", 25);

        Cenario enviado = aprovado().novaVersao(AUTOR).alterarRascunho(outroConteudo).enviarACuradoria(AUTOR);
        Cenario devolvido = enviado.devolver(CURADORA, "Rever a duração", AGORA);
        Cenario aprovadaANova = devolvido.enviarACuradoria(AUTOR).aprovar(CURADORA, AGORA);

        assertThat(enviado.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(enviado.versao().status()).isEqualTo(StatusDaVersao.EM_CURADORIA);
        assertThat(devolvido.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(devolvido.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(devolvido.ultimaDevolucao().comentario()).isEqualTo("Rever a duração");
        assertThat(aprovadaANova.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(aprovadaANova.versao().numero()).isEqualTo(2);
        assertThat(aprovadaANova.versao().status()).isEqualTo(StatusDaVersao.APROVADA);
        assertThat(aprovadaANova.versao().conteudo()).isEqualTo(outroConteudo);
    }

    /** RN-04: a marca de sugerido por IA é do cenário e continua na versão nova e em toda a curadoria dela. */
    @Test
    void versaoNovaGuardaAMarcaDeSugeridoPorIa() {
        Cenario aprovado = aprovado();
        var sugerido = new Cenario(aprovado.id(), AUTOR, aprovado.status(), aprovado.versao(), null, true, 40L);

        Cenario devolvida = sugerido.novaVersao(AUTOR).enviarACuradoria(AUTOR).devolver(CURADORA, "Rever", AGORA);

        assertThat(devolvida.sugeridoPorIa()).isTrue();
        assertThat(devolvida.sugestaoDaIa()).isEqualTo(40L);
    }

    @Test
    void soOAutorCriaVersao() {
        assertThatThrownBy(() -> aprovado().novaVersao(CURADORA.id()))
                .isInstanceOf(VersaoNaoPermitida.class)
                .hasMessageContaining("Só o autor");
    }

    /** Rascunho e em curadoria são editados na própria versão; o arquivado não muda mais (RN-09). */
    @ParameterizedTest
    @EnumSource(value = StatusDoCenario.class, names = {"RASCUNHO", "EM_CURADORIA", "ARQUIVADO"})
    void semVersaoVigenteNaoHaVersaoNova(StatusDoCenario status) {
        Cenario cenario = comStatus(aprovado(), status);

        assertThatThrownBy(() -> cenario.novaVersao(AUTOR))
                .isInstanceOf(VersaoNaoPermitida.class)
                .hasMessageContaining(status.name());
    }

    @Test
    void naoCriaOutraEnquantoHouverVersaoEmAndamento() {
        Cenario comRascunho = aprovado().novaVersao(AUTOR);
        Cenario comCuradoria = comRascunho.enviarACuradoria(AUTOR);

        assertThatThrownBy(() -> comRascunho.novaVersao(AUTOR))
                .isInstanceOf(VersaoNaoPermitida.class)
                .hasMessageContaining("versão 2 em RASCUNHO");
        assertThatThrownBy(() -> comCuradoria.novaVersao(AUTOR))
                .isInstanceOf(VersaoNaoPermitida.class)
                .hasMessageContaining("EM_CURADORIA");
    }

    @Test
    void restauraUmaVersaoAprovadaAnteriorComoAVersaoSeguinte() {
        VersaoDoCenario primeira = aprovado().versao();
        var conteudoNovo = new ConteudoDoCenario("Outro título", ConteudosDeTeste.ROTEIRO, ConteudosDeTeste.CURRICULO,
                "13 a 14 anos", 20);
        Cenario naSegunda = aprovado().novaVersao(AUTOR).alterarRascunho(conteudoNovo).enviarACuradoria(AUTOR)
                .aprovar(CURADORA, AGORA);

        Cenario restaurado = naSegunda.restaurar(AUTOR, primeira);

        assertThat(restaurado.versao().numero()).isEqualTo(3);
        assertThat(restaurado.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(restaurado.versao().conteudo()).isEqualTo(primeira.conteudo());
        assertThat(restaurado.status()).isEqualTo(StatusDoCenario.APROVADO);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDaVersao.class, names = {"RASCUNHO", "EM_CURADORIA"})
    void soRestauraVersaoAprovada(StatusDaVersao status) {
        var naoAprovada = new VersaoDoCenario(9L, 1, status, ConteudosDeTeste.completo(), AUTOR);

        assertThatThrownBy(() -> aprovado().restaurar(AUTOR, naoAprovada))
                .isInstanceOf(VersaoNaoPermitida.class)
                .hasMessageContaining("aprovada");
    }

    /** Restaurar a versão que já vale só criaria uma cópia para a curadoria revisar à toa. */
    @Test
    void naoRestauraAUltimaAprovada() {
        Cenario aprovado = aprovado();

        assertThatThrownBy(() -> aprovado.restaurar(AUTOR, aprovado.versao()))
                .isInstanceOf(VersaoNaoPermitida.class)
                .hasMessageContaining("já é a última aprovada");
    }

    /** A autoria vem antes de tudo: quem não é autor não descobre nada sobre as versões. */
    @Test
    void quemNaoEAutorERecusadoAntesDeOlharAVersao() {
        var naoAprovada = new VersaoDoCenario(9L, 1, StatusDaVersao.EM_CURADORIA, ConteudosDeTeste.completo(), AUTOR);

        assertThatThrownBy(() -> aprovado().restaurar(CURADORA.id(), naoAprovada))
                .isInstanceOf(VersaoNaoPermitida.class)
                .hasMessageContaining("Só o autor");
    }

    /** RN-37. */
    @Test
    void curadorVeQualquerCenarioEOProfessorSoOsSeus() {
        Cenario cenario = aprovado();

        assertThat(cenario.podeSerVistoPor(CURADORA)).isTrue();
        assertThat(cenario.podeSerVistoPor(new Revisor(AUTOR, false))).isTrue();
        assertThat(cenario.podeSerVistoPor(new Revisor(99L, false))).isFalse();
    }

    @Test
    void restaurarSegueAsMesmasRegrasDaVersaoNova() {
        VersaoDoCenario primeira = aprovado().versao();

        assertThatThrownBy(() -> aprovado().restaurar(CURADORA.id(), primeira)).isInstanceOf(VersaoNaoPermitida.class);
        assertThatThrownBy(() -> aprovado().novaVersao(AUTOR).restaurar(AUTOR, primeira))
                .isInstanceOf(VersaoNaoPermitida.class);
    }

    /** Sem versão vigente, a devolução e o envio continuam mudando o status do cenário (E1). */
    @Test
    void semVersaoVigenteOStatusDoCenarioAcompanhaACuradoria() {
        var versao = new VersaoDoCenario(2L, 1, StatusDaVersao.RASCUNHO, ConteudosDeTeste.completo(), AUTOR);
        Cenario rascunho = new Cenario(1L, AUTOR, StatusDoCenario.RASCUNHO, versao);

        Cenario enviado = rascunho.enviarACuradoria(AUTOR);

        assertThat(enviado.status()).isEqualTo(StatusDoCenario.EM_CURADORIA);
        assertThat(enviado.devolver(CURADORA, "Corrigir", AGORA).status()).isEqualTo(StatusDoCenario.RASCUNHO);
    }

    private static Cenario aprovado() {
        var versao = new VersaoDoCenario(2L, 1, StatusDaVersao.RASCUNHO, ConteudosDeTeste.completo(), AUTOR);
        return new Cenario(1L, AUTOR, StatusDoCenario.RASCUNHO, versao).enviarACuradoria(AUTOR).aprovar(CURADORA, AGORA);
    }

    private static Cenario comStatus(Cenario cenario, StatusDoCenario status) {
        return new Cenario(cenario.id(), cenario.autorId(), status, cenario.versao(), cenario.ultimaDevolucao(),
                cenario.sugeridoPorIa(), cenario.sugestaoDaIa());
    }
}
