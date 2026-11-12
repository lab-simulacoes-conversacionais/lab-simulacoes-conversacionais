package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.dominio.MensagemDoPrompt;
import br.ufg.inf.labsimulacoes.ia.dominio.PromptMontado;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Uma chamada presa ao provedor é cortada pelo tempo limite de leitura, tanto no streaming da conversa
 * quanto na moderação, e vira {@link FalhaNoProvedorDeIa}. O provedor aqui é um servidor local que
 * aceita a conexão e nunca responde; o tempo limite é encurtado para o teste.
 */
@SpringBootTest(properties = {
    "lab.teste.contexto=tempo-limite-do-provedor",
    "spring.http.client.read-timeout=1s",
    "spring.http.reactiveclient.read-timeout=1s",
    "spring.ai.retry.max-attempts=1"
})
@Import(InfraestruturaDeTeste.class)
class TempoLimiteDoProvedorTest {

    private static final PromptMontado PROMPT =
            new PromptMontado("teste-v1", "Você é Santos Dumont.", List.of(MensagemDoPrompt.doUsuario("Oi!")));

    /** Bem acima do tempo limite do teste e bem abaixo de uma espera sem fim. */
    private static final Duration PRAZO = Duration.ofSeconds(10);

    private static final CountDownLatch LIBERAR = new CountDownLatch(1);
    private static final HttpServer PROVEDOR_PRESO = provedorPreso();

    @Autowired
    private ProvedorDeIa provedor;

    @DynamicPropertySource
    static void apontarParaOProvedorPreso(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.ai.openai.base-url",
                () -> "http://localhost:" + PROVEDOR_PRESO.getAddress().getPort());
    }

    @AfterAll
    static void pararProvedor() {
        LIBERAR.countDown();
        PROVEDOR_PRESO.stop(0);
    }

    @Test
    void conversaPresaECortada() {
        assertTimeoutPreemptively(PRAZO, () -> assertThatThrownBy(() -> provedor.conversar(PROMPT, trecho -> {}))
                .isInstanceOf(FalhaNoProvedorDeIa.class));
    }

    @Test
    void moderacaoPresaECortada() {
        assertTimeoutPreemptively(PRAZO, () -> assertThatThrownBy(() -> provedor.moderar("Oi!"))
                .isInstanceOf(FalhaNoProvedorDeIa.class));
    }

    private static HttpServer provedorPreso() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            servidor.createContext("/", troca -> {
                try {
                    LIBERAR.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                troca.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException("Não deu para subir o provedor simulado", e);
        }
    }
}
