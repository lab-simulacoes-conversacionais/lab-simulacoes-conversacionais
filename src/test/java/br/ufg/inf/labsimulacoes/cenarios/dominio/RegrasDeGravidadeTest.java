package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static br.ufg.inf.labsimulacoes.cenarios.dominio.GravidadeDoErro.GRAVE;
import static br.ufg.inf.labsimulacoes.cenarios.dominio.GravidadeDoErro.LEVE;
import static br.ufg.inf.labsimulacoes.cenarios.dominio.GravidadeDoErro.MODERADA;
import static br.ufg.inf.labsimulacoes.cenarios.dominio.PerfilDeQuemSinalizou.CURADOR;
import static br.ufg.inf.labsimulacoes.cenarios.dominio.PerfilDeQuemSinalizou.PROFESSOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Regras de gravidade que suspendem o cenário (RN-23), sem banco nem Spring. */
class RegrasDeGravidadeTest {

    private static final long VERSAO = 10L;
    private static final long CURADORA = 20L;
    private static final Instant REGISTRO = Instant.parse("2026-11-27T13:00:00Z");
    private static final Instant TRIAGEM = Instant.parse("2026-11-27T14:00:00Z");

    @Test
    void versaoSemSinalizacaoNaoSuspende() {
        assertThat(RegrasDeGravidade.motivo(List.of())).isEmpty();
    }

    // RN-23a: a sinalização grave do professor suspende na hora, por precaução.
    @Test
    void graveDoProfessorSuspendeEnquantoEstiverPendente() {
        assertThat(RegrasDeGravidade.motivo(List.of(pendente(1, PROFESSOR, GRAVE))))
                .contains(MotivoDaSuspensao.SINALIZACAO_GRAVE);
    }

    /** Quem tem os dois perfis e sinaliza como curador não suspende na hora: decide o perfil gravado. */
    @Test
    void graveFeitaComoCuradorEsperaATriagem() {
        assertThat(RegrasDeGravidade.motivo(List.of(pendente(1, CURADOR, GRAVE)))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = GravidadeDoErro.class, names = {"LEVE", "MODERADA"})
    void professorComMenosQueGraveNaoSuspendeNaHora(GravidadeDoErro percebida) {
        assertThat(RegrasDeGravidade.motivo(List.of(pendente(1, PROFESSOR, percebida)))).isEmpty();
    }

    /** RN-23d: descartada a sinalização que suspendeu, a suspensão cai. */
    @Test
    void descarteDaGraveDoProfessorReativa() {
        assertThat(RegrasDeGravidade.motivo(List.of(descartada(1, PROFESSOR, GRAVE)))).isEmpty();
    }

    /** Confirmada com gravidade menor, a precaução cai; moderada passa a contar para o RN-23c. */
    @ParameterizedTest
    @EnumSource(value = GravidadeDoErro.class, names = {"LEVE", "MODERADA"})
    void rebaixamentoDaGraveDoProfessorReativa(GravidadeDoErro gravidadeFinal) {
        assertThat(RegrasDeGravidade.motivo(List.of(confirmada(1, PROFESSOR, GRAVE, gravidadeFinal)))).isEmpty();
    }

    @Test
    void rebaixadaParaModeradaContaComoModerada() {
        var sinalizacoes = List.of(
                confirmada(1, PROFESSOR, GRAVE, MODERADA),
                confirmada(2, CURADOR, MODERADA, MODERADA),
                confirmada(3, PROFESSOR, LEVE, MODERADA));

        assertThat(RegrasDeGravidade.motivo(sinalizacoes)).contains(MotivoDaSuspensao.TRES_ERROS_MODERADOS);
    }

    @Test
    void rebaixadaParaLeveNaoContaComoModerada() {
        var sinalizacoes = List.of(
                confirmada(1, PROFESSOR, GRAVE, LEVE),
                confirmada(2, CURADOR, MODERADA, MODERADA),
                confirmada(3, PROFESSOR, LEVE, MODERADA));

        assertThat(RegrasDeGravidade.motivo(sinalizacoes)).isEmpty();
    }

    // RN-23b: o erro grave confirmado mantém o cenário suspenso até uma versão nova ser aprovada.
    @ParameterizedTest
    @EnumSource(PerfilDeQuemSinalizou.class)
    void erroGraveConfirmadoSuspendeQualquerQueSejaOPerfil(PerfilDeQuemSinalizou perfil) {
        assertThat(RegrasDeGravidade.motivo(List.of(confirmada(1, perfil, MODERADA, GRAVE))))
                .contains(MotivoDaSuspensao.ERRO_GRAVE_CONFIRMADO);
    }

    /** A grave do professor confirmada como grave continua suspendendo, agora pelo erro confirmado. */
    @Test
    void graveDoProfessorConfirmadaComoGraveMantemASuspensao() {
        assertThat(RegrasDeGravidade.motivo(List.of(confirmada(1, PROFESSOR, GRAVE, GRAVE))))
                .contains(MotivoDaSuspensao.ERRO_GRAVE_CONFIRMADO);
    }

    /** Descartar outras sinalizações da versão não tira o motivo. */
    @Test
    void descarteDeOutraSinalizacaoNaoTiraOErroGraveConfirmado() {
        var sinalizacoes = List.of(confirmada(1, CURADOR, GRAVE, GRAVE), descartada(2, PROFESSOR, GRAVE));

        assertThat(RegrasDeGravidade.motivo(sinalizacoes)).contains(MotivoDaSuspensao.ERRO_GRAVE_CONFIRMADO);
    }

    /** Com mais de um motivo, vale o erro grave confirmado, que só sai com versão nova. */
    @Test
    void erroGraveConfirmadoValeMaisQueOsOutrosMotivos() {
        var sinalizacoes = List.of(
                pendente(1, PROFESSOR, GRAVE),
                confirmada(2, CURADOR, MODERADA, MODERADA),
                confirmada(3, CURADOR, MODERADA, MODERADA),
                confirmada(4, CURADOR, MODERADA, MODERADA),
                confirmada(5, CURADOR, GRAVE, GRAVE));

        assertThat(RegrasDeGravidade.motivo(sinalizacoes)).contains(MotivoDaSuspensao.ERRO_GRAVE_CONFIRMADO);
    }

    // RN-23c: três erros moderados confirmados na mesma versão também suspendem.
    @Test
    void tresModeradosConfirmadosSuspendem() {
        var sinalizacoes = List.of(
                confirmada(1, PROFESSOR, MODERADA, MODERADA),
                confirmada(2, CURADOR, LEVE, MODERADA),
                confirmada(3, PROFESSOR, MODERADA, MODERADA));

        assertThat(RegrasDeGravidade.motivo(sinalizacoes)).contains(MotivoDaSuspensao.TRES_ERROS_MODERADOS);
    }

    @Test
    void doisModeradosNaoSuspendem() {
        var sinalizacoes = List.of(
                confirmada(1, PROFESSOR, MODERADA, MODERADA), confirmada(2, CURADOR, MODERADA, MODERADA));

        assertThat(RegrasDeGravidade.motivo(sinalizacoes)).isEmpty();
    }

    /** Só conta a gravidade final: moderada pendente, descartada ou confirmada como leve fica de fora. */
    @Test
    void soContaOModeradoConfirmado() {
        var sinalizacoes = List.of(
                confirmada(1, PROFESSOR, MODERADA, MODERADA),
                confirmada(2, CURADOR, MODERADA, MODERADA),
                pendente(3, PROFESSOR, MODERADA),
                descartada(4, CURADOR, MODERADA),
                confirmada(5, PROFESSOR, MODERADA, LEVE));

        assertThat(RegrasDeGravidade.motivo(sinalizacoes)).isEmpty();
    }

    /** A grave confirmada suspende sozinha e não completa a conta dos moderados. */
    @Test
    void graveConfirmadaNaoEntraNaContaDosModerados() {
        var sinalizacoes = List.of(
                confirmada(1, PROFESSOR, MODERADA, MODERADA),
                confirmada(2, CURADOR, MODERADA, MODERADA),
                confirmada(3, CURADOR, GRAVE, GRAVE));

        assertThat(RegrasDeGravidade.motivo(sinalizacoes)).contains(MotivoDaSuspensao.ERRO_GRAVE_CONFIRMADO);
    }

    /** A contagem é por versão: sinalizações de outra versão não podem entrar na mesma conta. */
    @Test
    void sinalizacoesDeVersoesDiferentesSaoRecusadas() {
        var outraVersao = SinalizacaoDaVersao.confirmada(
                3, VERSAO + 1, CURADOR, MODERADA, MODERADA, CURADORA, REGISTRO, TRIAGEM);
        var sinalizacoes = List.of(
                confirmada(1, PROFESSOR, MODERADA, MODERADA), confirmada(2, CURADOR, MODERADA, MODERADA), outraVersao);

        assertThatThrownBy(() -> RegrasDeGravidade.motivo(sinalizacoes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // O cenário só volta a Aprovado quando nenhum motivo restar.
    @Test
    void descartadaAGraveDoProfessorContinuaSuspensoPelosModerados() {
        var sinalizacoes = List.of(
                descartada(1, PROFESSOR, GRAVE),
                confirmada(2, CURADOR, MODERADA, MODERADA),
                confirmada(3, CURADOR, MODERADA, MODERADA),
                confirmada(4, PROFESSOR, MODERADA, MODERADA));

        assertThat(RegrasDeGravidade.motivo(sinalizacoes)).contains(MotivoDaSuspensao.TRES_ERROS_MODERADOS);
    }

    @Test
    void graveDoProfessorValeMaisQueOsModerados() {
        var sinalizacoes = List.of(
                pendente(1, PROFESSOR, GRAVE),
                confirmada(2, CURADOR, MODERADA, MODERADA),
                confirmada(3, CURADOR, MODERADA, MODERADA),
                confirmada(4, PROFESSOR, MODERADA, MODERADA));

        assertThat(RegrasDeGravidade.motivo(sinalizacoes)).contains(MotivoDaSuspensao.SINALIZACAO_GRAVE);
    }

    @Test
    void duasGravesDoProfessorSoReativaDepoisDeTriarAsDuas() {
        var umaDescartada = List.of(descartada(1, PROFESSOR, GRAVE), pendente(2, PROFESSOR, GRAVE));
        var duasDescartadas = List.of(descartada(1, PROFESSOR, GRAVE), descartada(2, PROFESSOR, GRAVE));

        assertThat(RegrasDeGravidade.motivo(umaDescartada)).contains(MotivoDaSuspensao.SINALIZACAO_GRAVE);
        assertThat(RegrasDeGravidade.motivo(duasDescartadas)).isEmpty();
    }

    @Test
    void motivoDaReativacaoVemDaTriagem() {
        assertThat(MotivoDaReativacao.pelaTriagem(descartada(1, PROFESSOR, GRAVE)))
                .isEqualTo(MotivoDaReativacao.SINALIZACAO_DESCARTADA);
        assertThat(MotivoDaReativacao.pelaTriagem(confirmada(1, PROFESSOR, GRAVE, MODERADA)))
                .isEqualTo(MotivoDaReativacao.GRAVIDADE_REBAIXADA);
        assertThatThrownBy(() -> MotivoDaReativacao.pelaTriagem(pendente(1, PROFESSOR, GRAVE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SinalizacaoDaVersao pendente(long id, PerfilDeQuemSinalizou perfil, GravidadeDoErro percebida) {
        return SinalizacaoDaVersao.pendente(id, VERSAO, perfil, percebida, REGISTRO);
    }

    private static SinalizacaoDaVersao confirmada(
            long id, PerfilDeQuemSinalizou perfil, GravidadeDoErro percebida, GravidadeDoErro gravidadeFinal) {
        return SinalizacaoDaVersao.confirmada(
                id, VERSAO, perfil, percebida, gravidadeFinal, CURADORA, REGISTRO, TRIAGEM);
    }

    private static SinalizacaoDaVersao descartada(long id, PerfilDeQuemSinalizou perfil, GravidadeDoErro percebida) {
        return SinalizacaoDaVersao.descartada(id, VERSAO, perfil, percebida, CURADORA, REGISTRO, TRIAGEM);
    }
}
