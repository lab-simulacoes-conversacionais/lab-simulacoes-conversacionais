package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import br.ufg.inf.labsimulacoes.ia.dominio.MontadorDePrompt;
import br.ufg.inf.labsimulacoes.ia.dominio.MontadorProvisorio;
import br.ufg.inf.labsimulacoes.ia.dominio.TabelaDePrecos;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Objetos do domínio do adaptador que dependem da configuração. */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoDoAdaptador {

    @Bean
    TabelaDePrecos tabelaDePrecos(
            @Value("${ia.preco.entrada-por-milhao}") BigDecimal entradaPorMilhao,
            @Value("${ia.preco.saida-por-milhao}") BigDecimal saidaPorMilhao) {
        return new TabelaDePrecos(entradaPorMilhao, saidaPorMilhao);
    }

    /** Provisório até os templates versionados da issue #16. */
    @Bean
    MontadorDePrompt montadorDePrompt() {
        return new MontadorProvisorio();
    }
}
