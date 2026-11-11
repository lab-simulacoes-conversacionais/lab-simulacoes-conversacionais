package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import br.ufg.inf.labsimulacoes.ia.dominio.MontadorComTemplates;
import br.ufg.inf.labsimulacoes.ia.dominio.MontadorDePrompt;
import br.ufg.inf.labsimulacoes.ia.dominio.TabelaDePrecos;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/** Objetos do domínio do adaptador que dependem da configuração. */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoDoAdaptador {

    @Bean
    TabelaDePrecos tabelaDePrecos(
            @Value("${ia.preco.entrada-por-milhao}") BigDecimal entradaPorMilhao,
            @Value("${ia.preco.saida-por-milhao}") BigDecimal saidaPorMilhao) {
        return new TabelaDePrecos(entradaPorMilhao, saidaPorMilhao);
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
