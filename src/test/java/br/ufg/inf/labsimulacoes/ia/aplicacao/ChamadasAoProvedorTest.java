package br.ufg.inf.labsimulacoes.ia.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.ia.CenarioDaConversa;
import br.ufg.inf.labsimulacoes.ia.CenariosDeTeste;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.PedidoDeSugestao;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.ia.RoteiroSugerido;
import br.ufg.inf.labsimulacoes.ia.SugestaoDeCenario;
import br.ufg.inf.labsimulacoes.ia.dominio.Geracao;
import br.ufg.inf.labsimulacoes.ia.dominio.MetadadosDaResposta;
import br.ufg.inf.labsimulacoes.ia.dominio.PromptMontado;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.dominio.RegistroDeMetadados;
import br.ufg.inf.labsimulacoes.ia.dominio.TabelaDePrecos;
import br.ufg.inf.labsimulacoes.ia.dominio.TemplatesDeTeste;
import br.ufg.inf.labsimulacoes.ia.dominio.UsoDoModelo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Caso de uso com o provedor e o registro simulados; o montador, os templates e a tabela de preços são os reais. */
class ChamadasAoProvedorTest {

    private static final CenarioDaConversa CENARIO = CenariosDeTeste.VOO_DO_14_BIS;
    private static final PedidoDeFala PEDIDO = new PedidoDeFala(CENARIO, List.of(), "Quem é você?");
    private static final TabelaDePrecos PRECOS = new TabelaDePrecos(new BigDecimal("1.00"), new BigDecimal("5.00"));

    private ProvedorDeIa provedor;
    private RegistroDeMetadados registro;
    private ChamadasAoProvedor chamadas;

    @BeforeEach
    void criar() {
        provedor = mock(ProvedorDeIa.class);
        registro = mock(RegistroDeMetadados.class);
        when(provedor.nome()).thenReturn("openai");
        when(registro.gravar(any())).thenAnswer(gravacao -> gravacao.<MetadadosDaResposta>getArgument(0).comId(42));
        chamadas = new ChamadasAoProvedor(provedor, TemplatesDeTeste.montador(), registro, PRECOS);
    }

    @Test
    void respostaRepassaOsTrechosEGravaOsMetadados() {
        when(provedor.conversar(any(), any())).thenAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept("Sou ");
            aoReceber.accept("Santos Dumont.");
            return new Geracao<>("Sou Santos Dumont.", "gpt-6.1-terra-2026-10-01",
                    Map.of("reasoning_effort", "low"), new UsoDoModelo(316, 97));
        });
        List<String> trechos = new ArrayList<>();

        RespostaDoPersonagem resposta = chamadas.responder(PEDIDO, trechos::add);

        assertThat(trechos).containsExactly("Sou ", "Santos Dumont.");
        assertThat(resposta).isEqualTo(new RespostaDoPersonagem("Sou Santos Dumont.", 42));
        MetadadosDaResposta gravados = gravados();
        assertThat(gravados.provedor()).isEqualTo("openai");
        assertThat(gravados.modelo()).isEqualTo("gpt-6.1-terra-2026-10-01");
        assertThat(gravados.parametros()).containsExactly(Map.entry("reasoning_effort", "low"));
        assertThat(gravados.versaoPrompt()).isEqualTo(TemplatesDeTeste.PERSONAGEM);
        assertThat(gravados.uso()).isEqualTo(new UsoDoModelo(316, 97));
        assertThat(gravados.custo()).isEqualByComparingTo("0.000801");
        assertThat(gravados.latenciaMs()).isNotNegative();
    }

    @Test
    void promptEnviadoTerminaComAPerguntaDaTurma() {
        when(provedor.conversar(any(), any())).thenReturn(new Geracao<>("Olá.", "m", Map.of(), null));

        chamadas.responder(PEDIDO, trecho -> {});

        ArgumentCaptor<PromptMontado> prompt = ArgumentCaptor.forClass(PromptMontado.class);
        verify(provedor).conversar(prompt.capture(), any());
        assertThat(prompt.getValue().mensagens().getLast().texto()).isEqualTo("Quem é você?");
    }

    @Test
    void falhaDoProvedorNaoGravaMetadados() {
        when(provedor.conversar(any(), any())).thenThrow(new FalhaNoProvedorDeIa("fora do ar", null));

        assertThatThrownBy(() -> chamadas.responder(PEDIDO, trecho -> {})).isInstanceOf(FalhaNoProvedorDeIa.class);
        verify(registro, never()).gravar(any());
    }

    @Test
    void respostaVaziaFalhaDepoisDeGravarOsMetadados() {
        when(provedor.conversar(any(), any())).thenReturn(
                new Geracao<>(" ", "gpt-6.1-terra", Map.of(), new UsoDoModelo(316, 2048)));

        assertThatThrownBy(() -> chamadas.responder(PEDIDO, trecho -> {}))
                .isInstanceOf(FalhaNoProvedorDeIa.class)
                .hasMessageContaining("vazia");
        assertThat(gravados().uso()).isEqualTo(new UsoDoModelo(316, 2048));
    }

    @Test
    void erroDeQuemRecebeOsTrechosChegaSemMudancaESemMetadados() {
        var desconectou = new IllegalStateException("a tela fechou");
        when(provedor.conversar(any(), any())).thenAnswer(chamada -> {
            chamada.<Consumer<String>>getArgument(1).accept("Sou ");
            return null;
        });

        assertThatThrownBy(() -> chamadas.responder(PEDIDO, trecho -> {
                    throw desconectou;
                }))
                .isSameAs(desconectou);
        verify(registro, never()).gravar(any());
    }

    @Test
    void respostaExigePedidoEQuemRecebe() {
        assertThatThrownBy(() -> chamadas.responder(null, trecho -> {})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chamadas.responder(PEDIDO, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(registro);
    }

    @Test
    void sugestaoDevolveORoteiroEGravaOsMetadados() {
        var roteiro = new RoteiroSugerido("O voo do 14-bis", "Santos Dumont", null, null, null, null, null, null,
                null, 20);
        when(provedor.sugerirRoteiro(any())).thenReturn(
                new Geracao<>(roteiro, "gpt-6.1-terra", Map.of(), UsoDoModelo.desconhecido()));

        SugestaoDeCenario sugestao = chamadas.sugerirCenario(new PedidoDeSugestao("Voo", null, null, null, null));

        assertThat(sugestao).isEqualTo(new SugestaoDeCenario(roteiro, 42));
        MetadadosDaResposta gravados = gravados();
        assertThat(gravados.versaoPrompt()).isEqualTo(TemplatesDeTeste.SUGESTAO);
        assertThat(gravados.custo()).isNull();
    }

    @Test
    void sugestaoExigePedido() {
        assertThatThrownBy(() -> chamadas.sugerirCenario(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moderacaoRepassaAoProvedorSemGravarMetadados() {
        var sinalizado = new ResultadoDaModeracao(true, List.of("violence"));
        when(provedor.moderar("texto")).thenReturn(sinalizado);

        assertThat(chamadas.moderar("texto")).isEqualTo(sinalizado);
        verifyNoInteractions(registro);
    }

    @Test
    void moderacaoExigeTexto() {
        assertThatThrownBy(() -> chamadas.moderar(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chamadas.moderar(null)).isInstanceOf(IllegalArgumentException.class);
        verify(provedor, never()).moderar(any());
    }

    private MetadadosDaResposta gravados() {
        ArgumentCaptor<MetadadosDaResposta> metadados = ArgumentCaptor.forClass(MetadadosDaResposta.class);
        verify(registro).gravar(metadados.capture());
        return metadados.getValue();
    }
}
