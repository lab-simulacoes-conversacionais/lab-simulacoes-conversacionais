package br.ufg.inf.labsimulacoes.metricas.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.acesso.ParametrosDeUso;
import br.ufg.inf.labsimulacoes.ia.ConsumoDaIa;
import br.ufg.inf.labsimulacoes.ia.ResumoDoConsumo;
import br.ufg.inf.labsimulacoes.metricas.dominio.AlertaDeCusto;
import br.ufg.inf.labsimulacoes.metricas.dominio.AlertasDeCusto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** O que a administração lê do consumo, com as portas simuladas, para contar as consultas (RNF-13). */
class ConsumoDaIaNaAdministracaoTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private ConsumoDaIa consumo;
    private ParametrosDeUso parametros;
    private AlertasDeCusto alertas;
    private ConsumoDaIaNaAdministracao administracao;

    @BeforeEach
    void criar() {
        consumo = mock(ConsumoDaIa.class);
        parametros = mock(ParametrosDeUso.class);
        alertas = mock(AlertasDeCusto.class);
        administracao = new ConsumoDaIaNaAdministracao(consumo, parametros, alertas, FUSO);
        when(consumo.custoEntre(any(), any())).thenReturn(resumo("5.10"));
        when(parametros.custoDiarioMaximoUsd()).thenReturn(Optional.of(new BigDecimal("5")));
    }

    /** O dia é somado uma vez, e o aviso do topo vem dessa mesma soma. */
    @Test
    void hojeSomaODiaUmaVezEOAvisoReaproveitaASoma() {
        when(alertas.doDia(any())).thenReturn(Optional.of(alerta("5.02", "5")));

        var hoje = administracao.hoje();
        var aviso = hoje.aviso().orElseThrow();

        verify(consumo, times(1)).custoEntre(any(), any());
        assertThat(aviso.custoAgora()).isEqualByComparingTo("5.10");
        assertThat(aviso.limiteDoAlerta()).isEqualByComparingTo("5");
        assertThat(aviso.dia()).isEqualTo(hoje.dia());
    }

    @Test
    void semAlertaHojeNaoHaAviso() {
        when(alertas.doDia(any())).thenReturn(Optional.empty());

        assertThat(administracao.hoje().aviso()).isEmpty();
    }

    /** Nas outras abas, sem alerta hoje, o dia nem é somado. */
    @Test
    void avisoDeHojeSemAlertaNaoSomaODia() {
        when(alertas.doDia(any())).thenReturn(Optional.empty());

        assertThat(administracao.avisoDeHoje()).isEmpty();

        verify(consumo, never()).custoEntre(any(), any());
    }

    @Test
    void avisoDeHojeComAlertaSomaODiaUmaVez() {
        when(alertas.doDia(any())).thenReturn(Optional.of(alerta("5.02", "5")));

        assertThat(administracao.avisoDeHoje()).isPresent();

        verify(consumo, times(1)).custoEntre(any(), any());
    }

    /** A tela usa a mesma regra do alerta: igual ao limite ainda não passou. */
    @Test
    void passouDoLimiteSegueARegraDoAlerta() {
        when(alertas.doDia(any())).thenReturn(Optional.empty());
        when(consumo.custoEntre(any(), any())).thenReturn(resumo("5.00"));
        assertThat(administracao.hoje().passouDoLimite()).isFalse();

        when(consumo.custoEntre(any(), any())).thenReturn(resumo("5.000001"));
        assertThat(administracao.hoje().passouDoLimite()).isTrue();

        when(parametros.custoDiarioMaximoUsd()).thenReturn(Optional.empty());
        assertThat(administracao.hoje().passouDoLimite()).isFalse();
    }

    private static ResumoDoConsumo resumo(String custo) {
        return new ResumoDoConsumo(3, 10, 10, new BigDecimal(custo), 0);
    }

    private static AlertaDeCusto alerta(String custo, String limite) {
        return new AlertaDeCusto(LocalDate.now(FUSO), new BigDecimal(custo), new BigDecimal(limite));
    }
}
