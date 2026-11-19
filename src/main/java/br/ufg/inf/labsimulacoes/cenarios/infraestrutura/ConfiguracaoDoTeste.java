package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.LimiteDoTeste;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Teto de perguntas de teste por curador e versão, lido da configuração (RF-09, RNF-13). */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoDoTeste {

    @Bean
    LimiteDoTeste limiteDoTeste(@Value("${cenarios.teste.perguntas-por-versao}") int perguntasPorVersao) {
        return new LimiteDoTeste(perguntasPorVersao);
    }
}
