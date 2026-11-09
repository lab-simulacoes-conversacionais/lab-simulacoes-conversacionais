package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegistroDeAuditoriaTest {

    private static final Instant AGORA = Instant.parse("2026-11-08T13:45:10.123456Z");

    @Test
    void todaAcaoDoRn35TemUmaAcaoCritica() {
        assertThat(Arrays.stream(AcaoCritica.values()).map(Enum::name)).containsExactlyInAnyOrder(
                "APROVAR_CENARIO", "DEVOLVER_CENARIO", "SUSPENDER_CENARIO", "REATIVAR_CENARIO",
                "ARQUIVAR_CENARIO", "TRIAR_SINALIZACAO", "ALTERAR_PERFIS", "EXPORTAR_RELATORIO");
    }

    @Test
    void entidadeVemDaAcao() {
        assertThat(registro(AcaoCritica.APROVAR_CENARIO).entidade()).isEqualTo("cenario");
        assertThat(registro(AcaoCritica.ALTERAR_PERFIS).entidade()).isEqualTo("usuario");
        assertThat(registro(AcaoCritica.TRIAR_SINALIZACAO).entidade()).isEqualTo("sinalizacao");
        assertThat(registro(AcaoCritica.EXPORTAR_RELATORIO).entidade()).isEqualTo("relatorio");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void acaoCriticaExigeUsuario(long semUsuario) {
        assertThatThrownBy(() -> RegistroDeAuditoria.novo(AcaoCritica.APROVAR_CENARIO, semUsuario, "1", Map.of(), AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RN-35");
    }

    @Test
    void acaoCriticaExigeDataEHora() {
        assertThatThrownBy(() -> RegistroDeAuditoria.novo(AcaoCritica.APROVAR_CENARIO, 7, "1", Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RN-35");
    }

    @Test
    void acaoEObrigatoria() {
        assertThatThrownBy(() -> RegistroDeAuditoria.novo(null, 7, "1", Map.of(), AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registroNovoAindaNaoTemId() {
        RegistroDeAuditoria registro = registro(AcaoCritica.APROVAR_CENARIO);

        assertThat(registro.id()).isNull();
        assertThat(registro.usuarioId()).isEqualTo(7);
        assertThat(registro.entidadeId()).isEqualTo("1");
        assertThat(registro.ocorridoEm()).isEqualTo(AGORA);
    }

    @Test
    void detalhesNaoMudamDepoisDeCriado() {
        var detalhes = new HashMap<String, Object>(Map.of("versaoId", 3L));
        RegistroDeAuditoria registro = RegistroDeAuditoria.novo(AcaoCritica.APROVAR_CENARIO, 7, "1", detalhes, AGORA);

        detalhes.put("outro", "valor");

        assertThat(registro.detalhes()).containsOnlyKeys("versaoId");
        assertThatThrownBy(() -> registro.detalhes().put("outro", "valor"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void semDetalhesFicaVazio() {
        assertThat(RegistroDeAuditoria.novo(AcaoCritica.EXPORTAR_RELATORIO, 7, null, null, AGORA).detalhes()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // Hash encadeado
    // ---------------------------------------------------------------------------------------------

    @Test
    void registroNovoAindaNaoTemHash() {
        RegistroDeAuditoria registro = registro(AcaoCritica.APROVAR_CENARIO);

        assertThat(registro.hash()).isNull();
        assertThat(registro.confereCom(null)).isFalse();
    }

    @Test
    void encadearCalculaUmSha256EmHexadecimal() {
        RegistroDeAuditoria encadeado = registro(AcaoCritica.APROVAR_CENARIO).encadeadoApos(null);

        assertThat(encadeado.hash()).matches("[0-9a-f]{64}");
        assertThat(encadeado.confereCom(null)).isTrue();
    }

    @Test
    void hashDependeDaLinhaAnterior() {
        RegistroDeAuditoria registro = registro(AcaoCritica.APROVAR_CENARIO);
        RegistroDeAuditoria primeiro = registro.encadeadoApos(null);
        RegistroDeAuditoria seguinte = registro.encadeadoApos(primeiro.hash());

        assertThat(seguinte.hash()).isNotEqualTo(primeiro.hash());
        assertThat(seguinte.confereCom(primeiro.hash())).isTrue();
        assertThat(seguinte.confereCom(null)).isFalse();
    }

    @Test
    void ordemDasChavesDosDetalhesNaoMudaOHash() {
        var umaOrdem = new LinkedHashMap<String, Object>();
        umaOrdem.put("a", "1");
        umaOrdem.put("b", Map.of("y", 2L, "x", true));
        var outraOrdem = new LinkedHashMap<String, Object>();
        outraOrdem.put("b", Map.of("x", true, "y", 2L));
        outraOrdem.put("a", "1");

        assertThat(comDetalhes(umaOrdem).encadeadoApos("h").hash())
                .isEqualTo(comDetalhes(outraOrdem).encadeadoApos("h").hash());
    }

    @Test
    void inteiroLidoDoBancoComoIntConfereComOGravadoComoLong() {
        RegistroDeAuditoria gravado = comDetalhes(Map.of("versaoId", 3L)).encadeadoApos(null);
        RegistroDeAuditoria lido = new RegistroDeAuditoria(
                10L, 7, AcaoCritica.APROVAR_CENARIO, "1", Map.of("versaoId", 3), AGORA, gravado.hash());

        assertThat(lido.confereCom(null)).isTrue();
    }

    @Test
    void qualquerCampoAlteradoQuebraOHash() {
        RegistroDeAuditoria original = new RegistroDeAuditoria(
                10L, 7, AcaoCritica.APROVAR_CENARIO, "1", Map.of("versaoId", 3L), AGORA, null).encadeadoApos("h");
        String hash = original.hash();

        assertThat(original.confereCom("h")).isTrue();
        assertThat(List.of(
                new RegistroDeAuditoria(10L, 8, AcaoCritica.APROVAR_CENARIO, "1", Map.of("versaoId", 3L), AGORA, hash),
                new RegistroDeAuditoria(10L, 7, AcaoCritica.DEVOLVER_CENARIO, "1", Map.of("versaoId", 3L), AGORA, hash),
                new RegistroDeAuditoria(10L, 7, AcaoCritica.APROVAR_CENARIO, "2", Map.of("versaoId", 3L), AGORA, hash),
                new RegistroDeAuditoria(10L, 7, AcaoCritica.APROVAR_CENARIO, null, Map.of("versaoId", 3L), AGORA, hash),
                new RegistroDeAuditoria(10L, 7, AcaoCritica.APROVAR_CENARIO, "1", Map.of("versaoId", 4L), AGORA, hash),
                new RegistroDeAuditoria(10L, 7, AcaoCritica.APROVAR_CENARIO, "1", Map.of("versaoId", 3L),
                        AGORA.plusSeconds(1), hash)))
                .allSatisfy(alterado -> assertThat(alterado.confereCom("h")).isFalse());
    }

    @Test
    void textoComAspasEQuebraDeLinhaNaoSeConfundeComOutroCampo() {
        RegistroDeAuditoria comAspas = RegistroDeAuditoria.novo(
                AcaoCritica.DEVOLVER_CENARIO, 7, "1", Map.of("comentario", "a\",\"b\n\\"), AGORA);

        assertThat(comAspas.encadeadoApos(null).confereCom(null)).isTrue();
        assertThat(comAspas.encadeadoApos(null).hash())
                .isNotEqualTo(comDetalhes(Map.of("comentario", "a")).encadeadoApos(null).hash());
    }

    @Test
    void dataEHoraFicaNaPrecisaoDoBanco() {
        RegistroDeAuditoria registro = RegistroDeAuditoria.novo(
                AcaoCritica.APROVAR_CENARIO, 7, "1", Map.of(), Instant.parse("2026-11-08T13:45:10.123456789Z"));

        assertThat(registro.ocorridoEm()).isEqualTo(AGORA);
    }

    @Test
    void conjuntosViramListasParaAOrdemNaoMudar() {
        RegistroDeAuditoria registro = comDetalhes(Map.of("perfis", Set.of("CURADOR", "PROFESSOR")));

        assertThat(registro.detalhes().get("perfis")).isInstanceOf(List.class);
        assertThat(registro.encadeadoApos(null).confereCom(null)).isTrue();
    }

    @Test
    void detalhesNaoAceitamValorNulo() {
        var comNulo = new HashMap<String, Object>();
        comNulo.put("motivo", null);

        assertThatThrownBy(() -> comDetalhes(comNulo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detalhesNaoAceitamDecimais() {
        assertThatThrownBy(() -> comDetalhes(Map.of("custo", 1.5))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chavesDosDetalhesPrecisamSerTexto() {
        assertThatThrownBy(() -> comDetalhes(Map.of("filtros", Map.of(1, "a"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> comDetalhes(Map.of("", "a"))).isInstanceOf(IllegalArgumentException.class);
    }

    private static RegistroDeAuditoria comDetalhes(Map<String, ?> detalhes) {
        return RegistroDeAuditoria.novo(AcaoCritica.APROVAR_CENARIO, 7, "1", detalhes, AGORA);
    }

    private static RegistroDeAuditoria registro(AcaoCritica acao) {
        return RegistroDeAuditoria.novo(acao, 7, "1", Map.of(), AGORA);
    }
}
