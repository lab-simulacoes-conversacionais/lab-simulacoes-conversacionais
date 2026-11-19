package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.ia.dominio.MensagemDoPrompt;
import br.ufg.inf.labsimulacoes.ia.dominio.PromptMontado;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A moderação e o streaming da conversa usam a mesma conexão com o provedor (RNF-03, issue #80). O
 * provedor aqui é um servidor local que responde como a OpenAI e anota a porta de origem de cada
 * requisição: a mesma porta quer dizer a mesma conexão.
 */
@SpringBootTest(properties = "lab.teste.contexto=conexao-com-o-provedor")
@Import(InfraestruturaDeTeste.class)
class ConexaoComOProvedorTest {

    private static final PromptMontado PROMPT =
            new PromptMontado("teste-v1", "Você é Santos Dumont.", List.of(MensagemDoPrompt.doUsuario("Oi!")));

    private static final String MODERACAO = """
            {"id":"modr-1","model":"omni-moderation-latest","results":[{"flagged":false,
            "categories":{"hate":false,"violence":false},"category_scores":{"hate":0.0,"violence":0.0}}]}""";

    private static final String STREAMING = """
            data: {"id":"c1","object":"chat.completion.chunk","created":1,"model":"gpt-teste",\
            "choices":[{"index":0,"delta":{"role":"assistant","content":"Olá, turma."},"finish_reason":null}]}

            data: {"id":"c1","object":"chat.completion.chunk","created":1,"model":"gpt-teste",\
            "choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

            """;

    private static final List<Integer> PORTAS = new CopyOnWriteArrayList<>();
    private static final HttpServer PROVEDOR = provedorSimulado();

    @Autowired
    private ProvedorDeIa provedor;

    @DynamicPropertySource
    static void apontarParaOProvedorSimulado(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.ai.openai.base-url", () -> "http://localhost:" + PROVEDOR.getAddress().getPort());
    }

    @AfterAll
    static void pararProvedor() {
        PROVEDOR.stop(0);
    }

    @BeforeEach
    void limparPortas() {
        PORTAS.clear();
    }

    /**
     * Confere a ordem de uma pergunta: a moderação dela e, logo depois, o streaming da resposta. O
     * contrário (moderar a frase logo depois do streaming) não entra: aqui a conexão é HTTP/1.1, e o
     * streaming pode ainda não ter devolvido a conexão quando a moderação começa. Com o provedor, em
     * HTTP/2, as chamadas dividem a conexão mesmo ao mesmo tempo.
     */
    @Test
    void streamingUsaAConexaoAbertaPelaModeracao() {
        assertThat(provedor.moderar("Oi!").sinalizado()).isFalse();
        assertThat(provedor.conversar(PROMPT, trecho -> {}).conteudo()).isEqualTo("Olá, turma.");

        assertThat(PORTAS).as("portas de origem da moderação e do streaming").hasSize(2).containsOnly(PORTAS.get(0));
    }

    private static HttpServer provedorSimulado() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            servidor.createContext("/v1/moderations", troca -> responder(troca, "application/json", MODERACAO));
            servidor.createContext("/v1/chat/completions", troca -> responder(troca, "text/event-stream", STREAMING));
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException("Não deu para subir o provedor simulado", e);
        }
    }

    private static void responder(HttpExchange troca, String tipo, String corpo) throws IOException {
        PORTAS.add(troca.getRemoteAddress().getPort());
        troca.getRequestBody().readAllBytes();
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", tipo);
        troca.sendResponseHeaders(200, bytes.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }
}
