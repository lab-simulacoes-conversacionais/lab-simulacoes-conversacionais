package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.LogDeAuditoria;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistroNoLogTest {

    private static final RegistroDeAuditoria NOVO = RegistroDeAuditoria.novo(
            AcaoCritica.APROVAR_CENARIO, 7, "1", Map.of(), Instant.parse("2026-11-08T13:45:10Z"));

    private final LogDeAuditoria log = mock(LogDeAuditoria.class);
    private final RegistroNoLog registro = new RegistroNoLog(log);

    @BeforeEach
    void gravarDevolveOQueRecebeu() {
        when(log.gravar(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void encadeiaNaUltimaLinhaDoLog() {
        when(log.travarEBuscarUltimoHash()).thenReturn(Optional.of("anterior"));

        RegistroDeAuditoria gravado = registro.registrar(NOVO);

        assertThat(gravado.confereCom("anterior")).isTrue();
    }

    @Test
    void primeiraLinhaDoLogEncadeiaEmNada() {
        when(log.travarEBuscarUltimoHash()).thenReturn(Optional.empty());

        assertThat(registro.registrar(NOVO).confereCom(null)).isTrue();
    }

    @Test
    void travaOLogAntesDeGravar() {
        when(log.travarEBuscarUltimoHash()).thenReturn(Optional.empty());

        registro.registrar(NOVO);

        var ordem = inOrder(log);
        ordem.verify(log).travarEBuscarUltimoHash();
        ordem.verify(log).gravar(any());
    }
}
