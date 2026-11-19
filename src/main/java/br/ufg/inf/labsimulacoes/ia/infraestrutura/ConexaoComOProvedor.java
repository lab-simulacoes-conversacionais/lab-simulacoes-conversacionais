package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;

/**
 * Um cliente HTTP só para todas as chamadas ao provedor de IA (RNF-03, issue #80).
 *
 * <p>Sem isto, o Spring AI faz o streaming da conversa pelo {@code WebClient} e a moderação pelo
 * {@code RestClient}, cada um com o próprio cliente e as próprias conexões. Com um cliente só, as duas
 * chamadas usam a mesma conexão HTTP/2 com o provedor. A moderação da pergunta, feita logo antes da
 * geração, deixa a conexão aberta para o streaming, e ele não paga o handshake TCP e TLS mesmo depois
 * de um intervalo longo entre as perguntas.
 *
 * <p>Os tempos limite continuam os de {@code spring.http.client.*} (moderação e sugestão de roteiro) e
 * de {@code spring.http.reactiveclient.*} (streaming). Os customizers rodam depois dos do Boot, que
 * aplicam a fábrica e o conector padrão, e por isso valem sobre eles. Só o Spring AI usa esses clientes
 * na aplicação.
 */
@Configuration(proxyBeanMethods = false)
class ConexaoComOProvedor {

    /** Fechado com o contexto, junto com as conexões abertas. */
    @Bean(destroyMethod = "close")
    HttpClient clienteHttpDoProvedor(@Value("${spring.http.client.connect-timeout}") Duration conexao) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(conexao)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    RestClientCustomizer restClientNoClienteDoProvedor(
            HttpClient clienteHttpDoProvedor, @Value("${spring.http.client.read-timeout}") Duration leitura) {
        return builder -> {
            JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(clienteHttpDoProvedor);
            fabrica.setReadTimeout(leitura);
            builder.requestFactory(fabrica);
        };
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    WebClientCustomizer webClientNoClienteDoProvedor(
            HttpClient clienteHttpDoProvedor, @Value("${spring.http.reactiveclient.read-timeout}") Duration leitura) {
        return builder -> {
            JdkClientHttpConnector conector = new JdkClientHttpConnector(clienteHttpDoProvedor);
            conector.setReadTimeout(leitura);
            builder.clientConnector(conector);
        };
    }
}
