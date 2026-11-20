package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import br.ufg.inf.labsimulacoes.ia.dominio.MontadorComTemplates;
import br.ufg.inf.labsimulacoes.ia.dominio.MontadorDePrompt;
import br.ufg.inf.labsimulacoes.ia.dominio.PrecosPorUso;
import br.ufg.inf.labsimulacoes.ia.dominio.TabelaDePrecos;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/** Objetos do domínio do adaptador que dependem da configuração. */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoDoAdaptador {

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoDoAdaptador.class);

    /**
     * As tarifas da fala e da sugestão (RNF-13), cada uma do modelo e do nível de serviço que ela usa
     * (ia.preco.* e ia.sugestao.preco.*).
     */
    @Bean
    PrecosPorUso precosPorUso(
            @Value("${ia.preco.entrada-por-milhao}") BigDecimal falaEntradaPorMilhao,
            @Value("${ia.preco.saida-por-milhao}") BigDecimal falaSaidaPorMilhao,
            @Value("${ia.sugestao.preco.entrada-por-milhao}") BigDecimal sugestaoEntradaPorMilhao,
            @Value("${ia.sugestao.preco.saida-por-milhao}") BigDecimal sugestaoSaidaPorMilhao) {
        return new PrecosPorUso(
                new TabelaDePrecos(falaEntradaPorMilhao, falaSaidaPorMilhao),
                new TabelaDePrecos(sugestaoEntradaPorMilhao, sugestaoSaidaPorMilhao));
    }

    /**
     * Opções do modelo só para a sugestão de cenário (RNF-03, RNF-13). Sem IA_MODELO_SUGESTAO, a
     * aplicação sobe com a sugestão desligada, e o aviso fica no log da subida.
     */
    @Bean
    OpcoesDaSugestao opcoesDaSugestao(
            @Value("${ia.sugestao.modelo:}") String modelo,
            @Value("${ia.sugestao.reasoning-effort:}") String esforcoDeRaciocinio,
            @Value("${ia.sugestao.service-tier:}") String nivelDeServico,
            @Value("${ia.sugestao.max-tokens}") int tetoDeTokens) {
        var opcoes = new OpcoesDaSugestao(modelo, esforcoDeRaciocinio, nivelDeServico, tetoDeTokens);
        if (!opcoes.ligada()) {
            LOG.warn("A sugestão de cenário está desligada: defina a variável IA_MODELO_SUGESTAO com o modelo da"
                    + " sugestão. O chat continua funcionando.");
        }
        return opcoes;
    }

    /**
     * Montador com as versões de prompt configuradas (RNF-11). Uma versão sem arquivo, ou um template
     * inválido, impede a aplicação de subir.
     */
    @Bean
    MontadorDePrompt montadorDePrompt(
            ResourceLoader carregador,
            @Value("${ia.prompt.personagem}") String versaoDoPersonagem,
            @Value("${ia.prompt.sugestao}") String versaoDaSugestao) {
        LeitorDeTemplates leitor = new LeitorDeTemplates(carregador);
        return new MontadorComTemplates(
                leitor.ler(versaoDoPersonagem, "ia.prompt.personagem"), leitor.ler(versaoDaSugestao, "ia.prompt.sugestao"));
    }
}
