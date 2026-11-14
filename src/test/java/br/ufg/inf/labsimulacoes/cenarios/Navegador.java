package br.ufg.inf.labsimulacoes.cenarios;

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
import java.util.regex.Pattern;

/**
 * Cliente HTTP das telas de cenários: guarda os cookies, não segue redirecionamentos e manda o token
 * CSRF em cada formulário, como o navegador.
 */
final class Navegador {

    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    private final HttpClient cliente = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final int porta;
    private String csrf;

    Navegador(int porta) {
        this.porta = porta;
    }

    static Navegador logado(int porta, String email, String senha) throws IOException, InterruptedException {
        var navegador = new Navegador(porta);
        String token = navegador.tokenDe("/login");
        var resposta = navegador.postBruto("/login",
                "email=" + cod(email) + "&senha=" + cod(senha) + "&_csrf=" + cod(token));
        assertThat(local(resposta)).doesNotContain("/login");
        navegador.csrf = navegador.tokenDe("/login");
        return navegador;
    }

    HttpResponse<String> get(String caminho) throws IOException, InterruptedException {
        return cliente.send(requisicao(caminho).GET().build(), BodyHandlers.ofString());
    }

    HttpResponse<String> postForm(String caminho, String corpo) throws IOException, InterruptedException {
        return postBruto(caminho, corpo + "&_csrf=" + cod(csrf));
    }

    static String local(HttpResponse<String> resposta) {
        return resposta.headers().firstValue("Location").orElse("");
    }

    static String cod(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> postBruto(String caminho, String corpo) throws IOException, InterruptedException {
        return cliente.send(
                requisicao(caminho)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(BodyPublishers.ofString(corpo))
                        .build(),
                BodyHandlers.ofString());
    }

    private String tokenDe(String caminho) throws IOException, InterruptedException {
        var token = CAMPO_CSRF.matcher(get(caminho).body());
        assertThat(token.find()).as("token CSRF na página " + caminho).isTrue();
        return token.group(1);
    }

    private HttpRequest.Builder requisicao(String caminho) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho));
    }
}
