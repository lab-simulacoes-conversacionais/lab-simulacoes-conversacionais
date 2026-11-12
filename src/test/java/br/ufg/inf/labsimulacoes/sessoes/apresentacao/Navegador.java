package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cliente HTTP que guarda os cookies, não segue redirecionamentos e manda o CSRF como o HTMX. */
final class Navegador {

    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    private final HttpClient cliente = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final int porta;
    private String tokenCsrf;

    Navegador(int porta) {
        this.porta = porta;
    }

    void entrar(String email, String senha) throws IOException, InterruptedException {
        String corpo = "email=" + codificar(email) + "&senha=" + codificar(senha)
                + "&_csrf=" + codificar(csrfDe(get("/login").body()));
        var resposta = cliente.send(
                requisicao("/login")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(BodyPublishers.ofString(corpo))
                        .build(),
                BodyHandlers.ofString());
        assertThat(resposta.headers().firstValue("Location").orElse("")).doesNotContain("/login");
        // O token muda no login; a tela inicial traz o novo, como as telas que usam o HTMX.
        tokenCsrf = csrfDe(get("/").body());
    }

    HttpResponse<String> perguntar(long sessaoId, String texto) throws IOException, InterruptedException {
        return cliente.send(
                requisicao("/sessoes/" + sessaoId + "/perguntas")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("HX-Request", "true")
                        .header("X-CSRF-TOKEN", tokenCsrf)
                        .POST(BodyPublishers.ofString("texto=" + codificar(texto)))
                        .build(),
                BodyHandlers.ofString());
    }

    /** O SSE termina quando o servidor fecha o canal, então o corpo inteiro chega no fim. */
    HttpResponse<String> resposta(long sessaoId) throws IOException, InterruptedException {
        return cliente.send(
                requisicao("/sessoes/" + sessaoId + "/resposta")
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build(),
                BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String caminho) throws IOException, InterruptedException {
        return cliente.send(requisicao(caminho).GET().build(), BodyHandlers.ofString());
    }

    private HttpRequest.Builder requisicao(String caminho) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho));
    }

    private static String csrfDe(String pagina) {
        Matcher token = CAMPO_CSRF.matcher(pagina);
        assertThat(token.find()).as("a página traz o token CSRF").isTrue();
        return token.group(1);
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
