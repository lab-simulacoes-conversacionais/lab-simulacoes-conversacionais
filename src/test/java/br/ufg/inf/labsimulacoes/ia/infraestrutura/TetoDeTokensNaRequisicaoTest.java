package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.ia.dominio.MensagemDoPrompt;
import br.ufg.inf.labsimulacoes.ia.dominio.PromptMontado;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * RNF-13 e RNF-03 de ponta a ponta: com a configuração real e o cliente da OpenAI do Spring AI, o teto
 * de 4000 tokens chega ao corpo da requisição da sugestão, e a fala do personagem na sala sai sem ele.
 * O provedor aqui é um servidor local que guarda o corpo de cada requisição e responde como a OpenAI.
 */
@SpringBootTest(properties = {
    "lab.teste.contexto=teto-de-tokens-na-requisicao",
    "ia.sugestao.modelo=gpt-6.1-terra",
    "spring.ai.retry.max-attempts=1"
})
@Import(InfraestruturaDeTeste.class)
class TetoDeTokensNaRequisicaoTest {

    private static final PromptMontado PROMPT =
            new PromptMontado("teste-v1", "Você é Santos Dumont.", List.of(MensagemDoPrompt.doUsuario("Oi!")));

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final BlockingQueue<JsonNode> REQUISICOES = new LinkedBlockingQueue<>();
    private static final HttpServer PROVEDOR = provedorQueGuardaAsRequisicoes();

    @Autowired
    private ProvedorDeIa provedor;

    @DynamicPropertySource
    static void apontarParaOProvedorLocal(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.ai.openai.base-url", () -> "http://localhost:" + PROVEDOR.getAddress().getPort());
    }

    @AfterAll
    static void pararProvedor() {
        PROVEDOR.stop(0);
    }

    @BeforeEach
    void esquecerRequisicoes() {
        REQUISICOES.clear();
    }

    @Test
    void sugestaoVaiComOTetoDe4000Tokens() throws InterruptedException {
        provedor.sugerirRoteiro(PROMPT);

        JsonNode corpo = proximaRequisicao();
        assertThat(corpo.path("max_completion_tokens").asInt()).isEqualTo(4000);
        assertThat(corpo.has("max_tokens")).isFalse();
    }

    @Test
    void falaNaSalaSaiSemOTetoDaSugestao() throws InterruptedException {
        provedor.conversar(PROMPT, trecho -> {});

        JsonNode corpo = proximaRequisicao();
        assertThat(corpo.path("stream").asBoolean()).isTrue();
        assertThat(corpo.has("max_completion_tokens")).isFalse();
        assertThat(corpo.has("max_tokens")).isFalse();
    }

    private static JsonNode proximaRequisicao() throws InterruptedException {
        JsonNode corpo = REQUISICOES.poll(5, TimeUnit.SECONDS);
        assertThat(corpo).as("requisição ao provedor").isNotNull();
        return corpo;
    }

    private static HttpServer provedorQueGuardaAsRequisicoes() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", troca -> {
                try (troca) {
                    JsonNode corpo = JSON.readTree(troca.getRequestBody());
                    REQUISICOES.add(corpo);
                    if (corpo.path("stream").asBoolean()) {
                        responder(troca, "text/event-stream", trechoDaFala() + "data: [DONE]\n\n");
                    } else {
                        responder(troca, "application/json", respostaDaSugestao());
                    }
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException("Não deu para subir o provedor simulado", e);
        }
    }

    private static void responder(HttpExchange troca, String tipo, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", tipo);
        troca.sendResponseHeaders(200, bytes.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    private static String respostaDaSugestao() {
        return """
                {"id": "chatcmpl-1", "object": "chat.completion", "created": 1, "model": "gpt-6.1-terra",
                 "choices": [{"index": 0, "finish_reason": "stop",
                   "message": {"role": "assistant", "content": "{\\"titulo\\": \\"O voo do 14-bis\\"}"}}],
                 "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}}
                """;
    }

    private static String trechoDaFala() {
        return "data: {\"id\": \"chatcmpl-2\", \"object\": \"chat.completion.chunk\", \"created\": 1,"
                + " \"model\": \"gpt-6.1-terra\", \"choices\": [{\"index\": 0, \"finish_reason\": \"stop\","
                + " \"delta\": {\"role\": \"assistant\", \"content\": \"Sou Santos Dumont.\"}}]}\n\n";
    }
}
