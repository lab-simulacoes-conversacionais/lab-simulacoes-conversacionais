package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.ia.CenariosDeTeste;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.PedidoDeSugestao;
import br.ufg.inf.labsimulacoes.ia.dominio.MontadorDePrompt;
import br.ufg.inf.labsimulacoes.ia.dominio.PrecosPorUso;
import br.ufg.inf.labsimulacoes.ia.dominio.TabelaDePrecos;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Subida do montador com as versões de prompt configuradas: os templates vêm do classpath e uma versão
 * sem arquivo impede o contexto de subir (RNF-11).
 */
class ConfiguracaoDoAdaptadorTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracaoDoAdaptador.class)
            .withPropertyValues(
                    "ia.preco.entrada-por-milhao=1.00",
                    "ia.preco.saida-por-milhao=5.00",
                    "ia.sugestao.preco.entrada-por-milhao=0.50",
                    "ia.sugestao.preco.saida-por-milhao=2.50",
                    "ia.prompt.personagem=personagem-v1",
                    "ia.prompt.sugestao=sugestao-v1",
                    "ia.sugestao.modelo=gpt-6.1-terra",
                    "ia.sugestao.reasoning-effort=low",
                    "ia.sugestao.service-tier=default",
                    "ia.sugestao.max-tokens=4000");

    /** RNF-13: a fala e a sugestão têm cada uma a sua tarifa, lida da configuração. */
    @Test
    void precosDaFalaEDaSugestaoVemDaConfiguracao() {
        contexto.run(subido -> {
            PrecosPorUso precos = subido.getBean(PrecosPorUso.class);
            assertThat(precos.fala()).isEqualTo(new TabelaDePrecos(new BigDecimal("1.00"), new BigDecimal("5.00")));
            assertThat(precos.sugestao()).isEqualTo(new TabelaDePrecos(new BigDecimal("0.50"), new BigDecimal("2.50")));
        });
    }

    @Test
    void opcoesDaSugestaoVemDaConfiguracao() {
        contexto.run(subido -> assertThat(subido.getBean(OpcoesDaSugestao.class))
                .isEqualTo(new OpcoesDaSugestao("gpt-6.1-terra", "low", "default", 4000)));
    }

    /** Sem esforço nem nível próprios, a sugestão fica com os da fala. */
    @Test
    void esforcoENivelDaSugestaoSaoOpcionais() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConfiguracaoDoAdaptador.class)
                .withPropertyValues(
                        "ia.preco.entrada-por-milhao=1.00",
                        "ia.preco.saida-por-milhao=5.00",
                        "ia.sugestao.preco.entrada-por-milhao=0.50",
                        "ia.sugestao.preco.saida-por-milhao=2.50",
                    "ia.sugestao.preco.entrada-por-milhao=0.50",
                    "ia.sugestao.preco.saida-por-milhao=2.50",
                        "ia.prompt.personagem=personagem-v1",
                        "ia.prompt.sugestao=sugestao-v1",
                        "ia.sugestao.modelo=gpt-6.1-terra",
                        "ia.sugestao.max-tokens=4000")
                .run(subido -> assertThat(subido.getBean(OpcoesDaSugestao.class))
                        .isEqualTo(new OpcoesDaSugestao("gpt-6.1-terra", null, null, 4000)));
    }

    /** Sem IA_MODELO_SUGESTAO, a aplicação sobe com a sugestão desligada; o chat continua. */
    @ParameterizedTest
    @ValueSource(strings = {"ia.sugestao.modelo=", "ia.sugestao.modelo=  "})
    void semModeloDaSugestaoSobeComASugestaoDesligada(String propriedade) {
        contexto.withPropertyValues(propriedade).run(subido -> {
            assertThat(subido).hasNotFailed();
            assertThat(subido.getBean(OpcoesDaSugestao.class).ligada()).isFalse();
        });
    }

    @Test
    void carregaOsTemplatesDoClasspathComAVersao() {
        contexto.run(subido -> {
            assertThat(subido).hasNotFailed().hasSingleBean(MontadorDePrompt.class);
            MontadorDePrompt montador = subido.getBean(MontadorDePrompt.class);

            assertThat(montador.daFala(new PedidoDeFala(CenariosDeTeste.VOO_DO_14_BIS, List.of(), "Oi")).versao())
                    .isEqualTo("personagem-v1");
            assertThat(montador.daSugestao(new PedidoDeSugestao("Voo", null, null, null, null)).versao())
                    .isEqualTo("sugestao-v1");
        });
    }

    @Test
    void versaoDoPersonagemSemArquivoImpedeASubida() {
        contexto.withPropertyValues("ia.prompt.personagem=personagem-v99").run(subido -> {
            assertThat(subido).hasFailed();
            assertThat(subido.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Não há template de prompt para a versão \"personagem-v99\", configurada em"
                            + " ia.prompt.personagem: falta o arquivo prompts/personagem-v99.md");
        });
    }

    @Test
    void versaoDaSugestaoSemArquivoImpedeASubida() {
        contexto.withPropertyValues("ia.prompt.sugestao=sugestao-v99").run(subido -> {
            assertThat(subido).hasFailed();
            assertThat(subido.getStartupFailure()).rootCause()
                    .hasMessageContaining("\"sugestao-v99\"")
                    .hasMessageContaining("ia.prompt.sugestao");
        });
    }

    @Test
    void versaoComCaminhoOuMaiusculaERecusada() {
        contexto.withPropertyValues("ia.prompt.personagem=../application").run(subido ->
                assertThat(subido.getStartupFailure()).rootCause()
                        .hasMessageContaining("\"../application\"")
                        .hasMessageContaining("é inválida"));
        contexto.withPropertyValues("ia.prompt.sugestao=Sugestao-V1").run(subido ->
                assertThat(subido.getStartupFailure()).rootCause().hasMessageContaining("é inválida"));
    }

    /** O arquivo de teste em src/test/resources/prompts declara outra versão no cabeçalho. */
    @Test
    void cabecalhoComOutraVersaoImpedeASubida() {
        contexto.withPropertyValues("ia.prompt.personagem=teste-versao-divergente").run(subido ->
                assertThat(subido.getStartupFailure()).rootCause()
                        .hasMessage("Template de prompt teste-versao-divergente inválido: o cabeçalho declara a"
                                + " versão personagem-v1"));
    }

    @Test
    void templateComMarcadorDesconhecidoImpedeASubida() {
        contexto.withPropertyValues("ia.prompt.personagem=teste-marcador-desconhecido").run(subido ->
                assertThat(subido.getStartupFailure()).rootCause()
                        .hasMessageContaining("{{idade}}")
                        .hasMessageContaining("teste-marcador-desconhecido"));
    }
}
