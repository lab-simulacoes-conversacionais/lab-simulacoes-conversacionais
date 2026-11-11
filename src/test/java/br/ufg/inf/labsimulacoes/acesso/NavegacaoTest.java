package br.ufg.inf.labsimulacoes.acesso;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Critérios de aceite da issue 20: tela de login própria e início com navegação por perfil
 * (RF-01, RF-02, RN-36, RN-37). As requisições são HTTP reais, como nas demais telas protegidas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InfraestruturaDeTeste.class)
class NavegacaoTest {

    private static final String SENHA = "uma senha de teste";
    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @LocalServerPort
    private int porta;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Test
    void telaDeLoginPropriaTrazOFormularioEOTokenCsrf() throws Exception {
        var resposta = new Navegador().get("/login");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body())
                .contains("name=\"email\"")
                .contains("name=\"senha\"")
                .contains("name=\"_csrf\"")
                .contains("Boas-vindas");
    }

    @Test
    void telaDeLoginAvisaQuandoASessaoExpirou() throws Exception {
        var resposta = new Navegador().get("/login?expirada");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("Sua sess").contains("expirou");
    }

    @Test
    void professorVeSoAtalhosDeProfessor() throws Exception {
        String corpo = inicioComPerfis(Perfil.PROFESSOR);

        assertThat(corpo)
                .contains("Como professora ou professor")
                .contains("Iniciar sess")
                .doesNotContain("Como curadora ou curador")
                .doesNotContain("Como administradora ou administrador");
    }

    @Test
    void curadorVeSoAtalhosDeCuradoria() throws Exception {
        String corpo = inicioComPerfis(Perfil.CURADOR);

        assertThat(corpo)
                .contains("Como curadora ou curador")
                .contains("Fila do curador")
                .doesNotContain("Como professora ou professor");
    }

    @Test
    void administradorVeSoAtalhosDeAdministracao() throws Exception {
        String corpo = inicioComPerfis(Perfil.ADMINISTRADOR);

        assertThat(corpo)
                .contains("Como administradora ou administrador")
                .contains("Log de auditoria")
                .contains("Turmas")
                .doesNotContain("Como professora ou professor");
    }

    @Test
    void perfisAcumuladosMostramAsDuasNavegacoes() throws Exception {
        String corpo = inicioComPerfis(Perfil.PROFESSOR, Perfil.CURADOR);

        assertThat(corpo)
                .contains("Como professora ou professor")
                .contains("Como curadora ou curador");
    }

    @Test
    void inicioIdentificaQuemEstaLogadoEOfereceASaida() throws Exception {
        String email = "navegacao" + SEQUENCIA.incrementAndGet() + "@escola.test";
        cadastro.cadastrar("Profissional", email, SENHA, Set.of(Perfil.PROFESSOR));
        var navegador = new Navegador();
        navegador.entrar(email);

        var corpo = navegador.get("/").body();

        assertThat(corpo).contains(email).contains("Sair");
    }

    private String inicioComPerfis(Perfil... perfis) throws Exception {
        String email = "navegacao" + SEQUENCIA.incrementAndGet() + "@escola.test";
        cadastro.cadastrar("Profissional", email, SENHA, Set.of(perfis));
        var navegador = new Navegador();
        navegador.entrar(email);

        var resposta = navegador.get("/");
        assertThat(resposta.statusCode()).isEqualTo(200);
        return resposta.body();
    }

    /** Cliente HTTP que guarda os cookies e não segue redirecionamentos. */
    private final class Navegador {

        private final HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        void entrar(String email) throws IOException, InterruptedException {
            var token = CAMPO_CSRF.matcher(get("/login").body());
            assertThat(token.find()).as("a tela de login traz o token CSRF").isTrue();
            String corpo = "email=" + codificar(email) + "&senha=" + codificar(SENHA)
                    + "&_csrf=" + codificar(token.group(1));
            var resposta = cliente.send(
                    requisicao("/login")
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(BodyPublishers.ofString(corpo))
                            .build(),
                    BodyHandlers.ofString());
            assertThat(resposta.headers().firstValue("Location").orElse("")).doesNotContain("/login");
        }

        HttpResponse<String> get(String caminho) throws IOException, InterruptedException {
            return cliente.send(requisicao(caminho).GET().build(), BodyHandlers.ofString());
        }

        private HttpRequest.Builder requisicao(String caminho) {
            return HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho));
        }

        private String codificar(String valor) {
            return URLEncoder.encode(valor, StandardCharsets.UTF_8);
        }
    }
}


