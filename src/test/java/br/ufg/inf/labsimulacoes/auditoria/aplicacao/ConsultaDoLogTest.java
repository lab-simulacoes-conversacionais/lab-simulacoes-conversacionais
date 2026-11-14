package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.FiltroDoLog;
import br.ufg.inf.labsimulacoes.auditoria.dominio.LogDeAuditoria;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsultaDoLogTest {

    private final LogDeAuditoria log = mock(LogDeAuditoria.class);
    private final ConsultaDoLog consulta = new ConsultaDoLog(log);

    @Test
    void repassaOFiltroAoLog() {
        var filtro = new FiltroDoLog(null, null, 7L, AcaoCritica.APROVAR_CENARIO, 0, 20);
        var linha = RegistroDeAuditoria.novo(
                AcaoCritica.APROVAR_CENARIO, 7, "1", Map.of(), Instant.parse("2026-11-08T13:45:10Z"));
        when(log.consultar(filtro)).thenReturn(List.of(linha));

        assertThat(consulta.consultar(filtro)).containsExactly(linha);
    }

    @Test
    void semFiltroConsultaOLogInteiro() {
        consulta.consultar(null);

        verify(log).consultar(FiltroDoLog.tudo());
    }

    @Test
    void consultaNaoTemOperacaoQueAltereOLog() {
        assertThat(Arrays.stream(ConsultaDoLog.class.getDeclaredMethods())
                        .filter(metodo -> Modifier.isPublic(metodo.getModifiers()))
                        .map(Method::getName))
                .containsExactly("consultar");
    }
}
