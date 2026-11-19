package br.ufg.inf.labsimulacoes.ia.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.ObservacoesGravadas;
import br.ufg.inf.labsimulacoes.ObservacoesGravadas.Observada;
import br.ufg.inf.labsimulacoes.ia.CenarioDaConversa;
import br.ufg.inf.labsimulacoes.ia.CenariosDeTeste;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
    private ObservacoesGravadas observacoes;
    private SimpleMeterRegistry metricas;
    private ChamadasAoProvedor chamadas;

    @BeforeEach
    void criar() {
        provedor = mock(ProvedorDeIa.class);
        registro = mock(RegistroDeMetadados.class);
        when(provedor.nome()).thenReturn("openai");
        when(registro.gravar(any())).thenAnswer(gravacao -> gravacao.<MetadadosDaResposta>getArgument(0).comId(42));
        observacoes = new ObservacoesGravadas();
        metricas = new SimpleMeterRegistry();
        chamadas = new ChamadasAoProvedor(
                provedor, TemplatesDeTeste.montador(), registro, PRECOS, observacoes.registro(), metricas);
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

    /** RNF-13: os tokens de uma resposta que não virou roteiro ficam registrados. */
    @Test
    void sugestaoSemRoteiroFalhaDepoisDeGravarOsMetadados() {
        when(provedor.sugerirRoteiro(any())).thenReturn(
                new Geracao<>(null, "gpt-6.1-terra", Map.of(), new UsoDoModelo(500, 4000)));

        assertThatThrownBy(() -> chamadas.sugerirCenario(new PedidoDeSugestao("Voo", null, null, null, null)))
                .isInstanceOf(FalhaNoProvedorDeIa.class)
                .hasMessageContaining("roteiro");
        assertThat(gravados().uso()).isEqualTo(new UsoDoModelo(500, 4000));
    }

    @Test
    void falhaDoProvedorNaSugestaoNaoGravaMetadados() {
        when(provedor.sugerirRoteiro(any())).thenThrow(new FalhaNoProvedorDeIa("fora do ar", null));

        assertThatThrownBy(() -> chamadas.sugerirCenario(new PedidoDeSugestao("Voo", null, null, null, null)))
                .isInstanceOf(FalhaNoProvedorDeIa.class);
        verify(registro, never()).gravar(any());
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

    @Test
    void respostaObservaMontagemEsperaDoPrimeiroTrechoEGeracao() {
        List<String> etapasNoPrimeiroTrecho = new ArrayList<>();
        when(provedor.conversar(any(), any())).thenAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept("Sou ");
            aoReceber.accept("Santos Dumont.");
            return new Geracao<>("Sou Santos Dumont.", "m", Map.of(), null);
        });

        chamadas.responder(PEDIDO, trecho -> {
            if (etapasNoPrimeiroTrecho.isEmpty()) {
                etapasNoPrimeiroTrecho.addAll(etapas());
            }
        });

        // A espera para no primeiro trecho, antes de ele chegar a quem chamou.
        assertThat(etapasNoPrimeiroTrecho).containsExactly("montagem-do-prompt", "espera-do-primeiro-trecho");
        assertThat(etapas()).containsExactly("montagem-do-prompt", "espera-do-primeiro-trecho", "geracao");
        assertThat(observacoes.paradas(ChamadasAoProvedor.OBSERVACAO)).allMatch(o -> o.erro() == null);
    }

    @Test
    void falhaAntesDoPrimeiroTrechoParaAEsperaComOErro() {
        var foraDoAr = new FalhaNoProvedorDeIa("fora do ar", null);
        when(provedor.conversar(any(), any())).thenThrow(foraDoAr);

        assertThatThrownBy(() -> chamadas.responder(PEDIDO, trecho -> {})).isSameAs(foraDoAr);

        assertThat(observada("espera-do-primeiro-trecho").erro()).isSameAs(foraDoAr);
        assertThat(observada("geracao").erro()).isSameAs(foraDoAr);
    }

    @Test
    void respostaSemTrechoParaAEsperaComErro() {
        when(provedor.conversar(any(), any())).thenReturn(new Geracao<>("", "m", Map.of(), null));

        assertThatThrownBy(() -> chamadas.responder(PEDIDO, trecho -> {})).isInstanceOf(FalhaNoProvedorDeIa.class);

        assertThat(observada("espera-do-primeiro-trecho").erro()).isInstanceOf(FalhaNoProvedorDeIa.class);
        assertThat(observada("geracao").erro()).isNull();
    }

    @Test
    void moderacaoEObservada() {
        when(provedor.moderar("texto")).thenReturn(new ResultadoDaModeracao(false, List.of()));

        chamadas.moderar("texto");

        assertThat(etapas()).containsExactly("moderacao");
    }

    /** RNF-13: a finalidade e a referência de quem chamou vão para os metadados. */
    @Test
    void respostaGravaAFinalidadeEAReferenciaDoPedido() {
        when(provedor.conversar(any(), any())).thenReturn(new Geracao<>("Olá.", "m", Map.of(), null));

        chamadas.responder(new PedidoDeFala(CENARIO, List.of(), "Oi", FinalidadeDoConsumo.FALA_EM_SALA, 7L), t -> {});

        assertThat(gravados().finalidade()).isEqualTo(FinalidadeDoConsumo.FALA_EM_SALA);
        assertThat(gravados().referenciaId()).isEqualTo(7L);
    }

    @Test
    void pedidoSemFinalidadeGravaOsMetadadosSemEla() {
        when(provedor.conversar(any(), any())).thenReturn(new Geracao<>("Olá.", "m", Map.of(), null));

        chamadas.responder(PEDIDO, trecho -> {});

        assertThat(gravados().finalidade()).isNull();
        assertThat(gravados().referenciaId()).isNull();
    }

    @Test
    void sugestaoGravaAFinalidadeSugestaoSemReferencia() {
        when(provedor.sugerirRoteiro(any())).thenReturn(new Geracao<>(roteiro(), "m", Map.of(), null));

        chamadas.sugerirCenario(new PedidoDeSugestao("Voo", null, null, null, null));

        assertThat(gravados().finalidade()).isEqualTo(FinalidadeDoConsumo.SUGESTAO);
        assertThat(gravados().referenciaId()).isNull();
    }

    /** RNF-13: custo e tokens por finalidade e modelo, sem nenhum id como tag. */
    @Test
    void consumoDaRespostaViraContadoresComAsTags() {
        when(provedor.conversar(any(), any())).thenReturn(
                new Geracao<>("Olá.", "gpt-6.1-terra", Map.of(), new UsoDoModelo(316, 97)));

        chamadas.responder(new PedidoDeFala(CENARIO, List.of(), "Oi", FinalidadeDoConsumo.FALA_EM_SALA, 7L), t -> {});

        assertThat(metricas.get(ChamadasAoProvedor.CUSTO)
                        .tags(ChamadasAoProvedor.FINALIDADE, "fala-em-sala", ChamadasAoProvedor.MODELO, "gpt-6.1-terra")
                        .counter().count())
                .isEqualTo(0.000801);
        assertThat(tokens("fala-em-sala", "entrada")).isEqualTo(316);
        assertThat(tokens("fala-em-sala", "saida")).isEqualTo(97);
        assertThat(metricas.getMeters())
                .filteredOn(m -> m.getId().getName().startsWith("lab.ia."))
                .allSatisfy(m -> assertThat(m.getId().getTags()).extracting(t -> t.getKey())
                        .isSubsetOf(ChamadasAoProvedor.FINALIDADE, ChamadasAoProvedor.MODELO, ChamadasAoProvedor.TIPO,
                                ChamadasAoProvedor.MOTIVO, ChamadasAoProvedor.CHAVE, "error"));
        assertThat(metricas.getMeters()).flatMap(m -> m.getId().getTags()).extracting(t -> t.getValue())
                .doesNotContain("7");
    }

    @Test
    void consumoSemFinalidadeContaComoNaoInformada() {
        when(provedor.conversar(any(), any())).thenReturn(
                new Geracao<>("Olá.", "m", Map.of(), new UsoDoModelo(10, 20)));

        chamadas.responder(PEDIDO, trecho -> {});

        assertThat(tokens("nao-informada", "entrada")).isEqualTo(10);
    }

    /** Sem tokens informados não há custo: nenhum contador de custo ou de tokens é criado. */
    @Test
    void consumoDesconhecidoNaoSomaNada() {
        when(provedor.conversar(any(), any())).thenReturn(new Geracao<>("Olá.", "m", Map.of(), null));

        chamadas.responder(PEDIDO, trecho -> {});

        assertThat(metricas.find(ChamadasAoProvedor.CUSTO).counters()).isEmpty();
        assertThat(metricas.find(ChamadasAoProvedor.TOKENS).counters()).isEmpty();
    }

    @Test
    void respostaVaziaContaComoFalha() {
        when(provedor.conversar(any(), any())).thenReturn(
                new Geracao<>(" ", "m", Map.of(), new UsoDoModelo(316, 2048)));

        assertThatThrownBy(() -> chamadas.responder(PEDIDO, trecho -> {})).isInstanceOf(FalhaNoProvedorDeIa.class);

        assertThat(falhas("resposta-vazia")).isEqualTo(1);
        assertThat(tokens("nao-informada", "saida")).isEqualTo(2048);
    }

    @Test
    void sugestaoSemRoteiroContaComoFalha() {
        when(provedor.sugerirRoteiro(any())).thenReturn(new Geracao<>(null, "m", Map.of(), new UsoDoModelo(5, 6)));

        assertThatThrownBy(() -> chamadas.sugerirCenario(new PedidoDeSugestao("Voo", null, null, null, null)))
                .isInstanceOf(FalhaNoProvedorDeIa.class);

        assertThat(falhas("sem-roteiro")).isEqualTo(1);
        assertThat(tokens("sugestao", "entrada")).isEqualTo(5);
    }

    @Test
    void respostaComConteudoNaoContaFalha() {
        when(provedor.conversar(any(), any())).thenReturn(new Geracao<>("Olá.", "m", Map.of(), null));

        chamadas.responder(PEDIDO, trecho -> {});

        assertThat(metricas.find(ChamadasAoProvedor.FALHA).counters()).isEmpty();
    }

    /** RNF-13: a sugestão também é medida, como etapa própria. */
    @Test
    void sugestaoEObservada() {
        when(provedor.sugerirRoteiro(any())).thenReturn(new Geracao<>(roteiro(), "m", Map.of(), null));

        chamadas.sugerirCenario(new PedidoDeSugestao("Voo", null, null, null, null));

        assertThat(etapas()).containsExactly("sugestao");
    }

    @Test
    void falhaDaSugestaoFicaNaObservacao() {
        when(provedor.sugerirRoteiro(any())).thenThrow(new FalhaNoProvedorDeIa("fora do ar", null));

        assertThatThrownBy(() -> chamadas.sugerirCenario(new PedidoDeSugestao("Voo", null, null, null, null)))
                .isInstanceOf(FalhaNoProvedorDeIa.class);

        assertThat(observada("sugestao").erro()).isInstanceOf(FalhaNoProvedorDeIa.class);
    }

    private double tokens(String finalidade, String tipo) {
        return metricas.get(ChamadasAoProvedor.TOKENS)
                .tags(ChamadasAoProvedor.FINALIDADE, finalidade, ChamadasAoProvedor.TIPO, tipo)
                .counters().stream().mapToDouble(c -> c.count()).sum();
    }

    private double falhas(String motivo) {
        return metricas.get(ChamadasAoProvedor.FALHA).tag(ChamadasAoProvedor.MOTIVO, motivo).counter().count();
    }

    private static RoteiroSugerido roteiro() {
        return new RoteiroSugerido("O voo do 14-bis", "Santos Dumont", null, null, null, null, null, null, null, 20);
    }

    private List<String> etapas() {
        return observacoes.paradas(ChamadasAoProvedor.OBSERVACAO).stream()
                .map(o -> o.valor(ChamadasAoProvedor.CHAVE))
                .toList();
    }

    private Observada observada(String etapa) {
        return observacoes.paradas(ChamadasAoProvedor.OBSERVACAO).stream()
                .filter(o -> etapa.equals(o.valor(ChamadasAoProvedor.CHAVE)))
                .findFirst()
                .orElseThrow();
    }

    private MetadadosDaResposta gravados() {
        ArgumentCaptor<MetadadosDaResposta> metadados = ArgumentCaptor.forClass(MetadadosDaResposta.class);
        verify(registro).gravar(metadados.capture());
        return metadados.getValue();
    }
}
