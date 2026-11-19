package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.LimiteDeSugestoes;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Limite de pedidos de sugestão de cenário por usuário, lido da configuração (RNF-13). */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoDaSugestao {

    @Bean
    LimiteDeSugestoes limiteDeSugestoes(
            @Value("${cenarios.sugestao.pedidos-por-janela}") int pedidosPorJanela,
            @Value("${cenarios.sugestao.janela}") Duration janela) {
        return new LimiteDeSugestoes(pedidosPorJanela, janela);
    }
}
