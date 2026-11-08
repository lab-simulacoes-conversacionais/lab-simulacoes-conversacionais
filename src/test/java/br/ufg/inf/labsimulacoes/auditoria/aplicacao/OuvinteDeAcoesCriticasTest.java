package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import br.ufg.inf.labsimulacoes.eventos.PerfilAlterado;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tradução de cada evento para a linha do log, sem banco. */
class OuvinteDeAcoesCriticasTest {

    private static final Instant MOMENTO = Instant.parse("2026-11-08T13:45:10Z");

    private final RegistroNoLog registro = mock(RegistroNoLog.class);
    private final OuvinteDeAcoesCriticas ouvinte = new OuvinteDeAcoesCriticas(registro);

    @BeforeEach
    void registrarDevolveOQueRecebeu() {
        when(registro.registrar(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void alteracaoDePerfilRegistraQuemAlterouEQuemFoiAlterado() {
        ouvinte.perfilAlterado(new PerfilAlterado(
                2, 1, Set.of("PROFESSOR"), Set.of("PROFESSOR", "CURADOR"), MOMENTO));

        RegistroDeAuditoria linha = registrado();
        assertThat(linha.acao()).isEqualTo(AcaoCritica.ALTERAR_PERFIS);
        assertThat(linha.usuarioId()).isEqualTo(1);
        assertThat(linha.entidadeId()).isEqualTo("2");
        assertThat(linha.ocorridoEm()).isEqualTo(MOMENTO);
        assertThat(linha.detalhes().get("perfisAnteriores")).isEqualTo(List.of("PROFESSOR"));
        assertThat(linha.detalhes().get("perfisNovos")).isEqualTo(List.of("CURADOR", "PROFESSOR"));
    }

    private RegistroDeAuditoria registrado() {
        var linha = ArgumentCaptor.forClass(RegistroDeAuditoria.class);
        verify(registro).registrar(linha.capture());
        return linha.getValue();
    }
}
