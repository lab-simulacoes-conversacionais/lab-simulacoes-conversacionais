package br.ufg.inf.labsimulacoes.metricas.infraestrutura;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as tarefas agendadas, como a conferência do custo diário da IA (RNF-13), a cada
 * {@code metricas.custo.verificacao}. Os testes desligam pela propriedade
 * {@code metricas.custo.verificar-automaticamente} e chamam a conferência eles mesmos, como fazem com a
 * importação da BNCC.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "metricas.custo.verificar-automaticamente", havingValue = "true")
class AgendamentoDaVerificacao {}
