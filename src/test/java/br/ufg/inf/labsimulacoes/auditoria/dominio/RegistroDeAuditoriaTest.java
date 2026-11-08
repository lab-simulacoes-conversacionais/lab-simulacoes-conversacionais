package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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

    private static RegistroDeAuditoria registro(AcaoCritica acao) {
        return RegistroDeAuditoria.novo(acao, 7, "1", Map.of(), AGORA);
    }
}
