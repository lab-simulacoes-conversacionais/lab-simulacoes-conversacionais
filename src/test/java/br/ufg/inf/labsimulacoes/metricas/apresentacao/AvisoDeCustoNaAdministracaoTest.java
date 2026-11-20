package br.ufg.inf.labsimulacoes.metricas.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.ia.ResumoDoConsumo;
import br.ufg.inf.labsimulacoes.metricas.aplicacao.ConsumoDaIaNaAdministracao;
import br.ufg.inf.labsimulacoes.metricas.aplicacao.ConsumoDaIaNaAdministracao.AvisoDeCusto;
import br.ufg.inf.labsimulacoes.metricas.aplicacao.ConsumoDaIaNaAdministracao.ConsumoDoDia;
import br.ufg.inf.labsimulacoes.metricas.dominio.AlertaDeCusto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * RNF-13: o aviso de custo só consulta o banco quando vai haver tela para mostrá-lo, e a aba Consumo soma
 * o dia uma vez só. Sem Spring: o caso de uso é simulado, para contar as chamadas.
 */
class AvisoDeCustoNaAdministracaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 20);
    private static final AvisoDeCusto AVISO = new AvisoDeCusto(HOJE, new BigDecimal("5.10"), new BigDecimal("5"));

    private ConsumoDaIaNaAdministracao consumo;
    private AvisoDeCustoNaAdministracao advice;

    @BeforeEach
    void criar() {
        consumo = mock(ConsumoDaIaNaAdministracao.class);
        advice = new AvisoDeCustoNaAdministracao(consumo);
        when(consumo.avisoDeHoje()).thenReturn(Optional.of(AVISO));
    }

    @Test
    void getDeUmaTelaDaAdministracaoMostraOAviso() {
        var modelo = new ExtendedModelMap();

        advice.avisoDeCusto(requisicao("GET", "/admin/parametros"), modelo);

        assertThat(modelo.get(AvisoDeCustoNaAdministracao.ATRIBUTO)).isEqualTo(AVISO);
        verify(consumo, times(1)).avisoDeHoje();
    }

    /** Um POST que só redireciona não mostra tela: nenhuma consulta. */
    @Test
    void postNaoConsulta() {
        var modelo = new ExtendedModelMap();

        advice.avisoDeCusto(requisicao("POST", "/admin/parametros"), modelo);

        assertThat(modelo).doesNotContainKey(AvisoDeCustoNaAdministracao.ATRIBUTO);
        verifyNoInteractions(consumo);
    }

    /** Um fragmento HTMX não tem o topo da administração: nenhuma consulta. */
    @Test
    void requisicaoHtmxNaoConsulta() {
        var requisicao = requisicao("GET", "/admin/usuarios");
        requisicao.addHeader(AvisoDeCustoNaAdministracao.CABECALHO_DO_HTMX, "true");

        advice.avisoDeCusto(requisicao, new ExtendedModelMap());

        verifyNoInteractions(consumo);
    }

    @Test
    void foraDaAdministracaoNaoConsulta() {
        advice.avisoDeCusto(requisicao("GET", "/biblioteca"), new ExtendedModelMap());
        advice.avisoDeCusto(requisicao("GET", "/administrativo"), new ExtendedModelMap());

        verifyNoInteractions(consumo);
    }

    /** A aba Consumo põe o aviso ela mesma, com a soma que já fez: o advice não consulta. */
    @Test
    void abaConsumoNaoConsultaPeloAdvice() throws Exception {
        var requisicao = requisicao("GET", "/admin/consumo");
        var controller = new ConsumoController(consumo);
        requisicao.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(controller, ConsumoController.class.getDeclaredMethod(
                        "consumo", Long.class, org.springframework.ui.Model.class)));

        advice.avisoDeCusto(requisicao, new ExtendedModelMap());

        verifyNoInteractions(consumo);
    }

    /** Em GET /admin/consumo o dia é somado uma vez, pelo hoje(), e o aviso sai dessa soma. */
    @Test
    void abaConsumoSomaODiaUmaVezEPoeOAviso() {
        var hoje = new ConsumoDoDia(HOJE, new ResumoDoConsumo(3, 10, 10, new BigDecimal("5.10"), 0),
                new BigDecimal("5"), new AlertaDeCusto(HOJE, new BigDecimal("5.02"), new BigDecimal("5")));
        when(consumo.hoje()).thenReturn(hoje);
        var modelo = new ExtendedModelMap();

        String tela = new ConsumoController(consumo).consumo(null, modelo);

        assertThat(tela).isEqualTo("admin/consumo");
        assertThat(modelo.get(AvisoDeCustoNaAdministracao.ATRIBUTO)).isEqualTo(AVISO);
        verify(consumo, times(1)).hoje();
        verify(consumo, never()).avisoDeHoje();
    }

    private static MockHttpServletRequest requisicao(String metodo, String caminho) {
        return new MockHttpServletRequest(metodo, caminho);
    }
}
