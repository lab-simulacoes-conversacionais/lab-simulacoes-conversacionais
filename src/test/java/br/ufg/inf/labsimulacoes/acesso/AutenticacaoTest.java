package br.ufg.inf.labsimulacoes.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.apache.catalina.Context;
import org.apache.catalina.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Critérios de aceite da issue 5 (RF-01, RNF-09), com requisições HTTP reais contra o Tomcat, para
 * exercitar a cadeia de filtros e a expiração de verdade, e não uma simulação delas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({InfraestruturaDeTeste.class, AutenticacaoTest.Rotas.class})
class AutenticacaoTest {

    private static final String SENHA = "uma senha de teste";
    private static final String HTMX = "HX-Request";
    private static final String CABECALHO_CSRF = "X-CSRF-TOKEN";
    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @LocalServerPort
    private int porta;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ServletWebServerApplicationContext contexto;

    /** Rotas que só existem nos testes, porque o sistema ainda não tem telas protegidas. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Rotas {

        @Bean
        RotasDeTeste rotasDeTeste() {
            return new RotasDeTeste();
        }
    }

    @RestController
    static class RotasDeTeste {

        /** Faz o papel da página que entrega o token ao HTMX pelo layout. */
        @GetMapping("/teste/csrf")
        String csrf(CsrfToken token) {
            return token.getToken();
        }

        @PostMapping("/teste/acao")
        String acao() {
            return "ok";
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------------------------------------

    @Test
    void semLoginTudoLevaParaATelaDeLogin() throws Exception {
        var resposta = new Navegador().get("/teste/csrf");

        assertThat(resposta.statusCode()).isEqualTo(302);
        assertThat(destino(resposta)).endsWith("/login");
    }

    @Test
    void verificacaoDeSaudeNaoExigeLogin() throws Exception {
        assertThat(new Navegador().get("/actuator/health").statusCode()).isEqualTo(200);
    }

    @Test
    void usuarioAtivoEntraComEmailESenha() throws Exception {
        var navegador = new Navegador();

        navegador.entrar(novoUsuario(), SENHA);

        assertThat(navegador.get("/teste/csrf").statusCode()).isEqualTo(200);
    }

    @Test
    void emailNaoDiferenciaMaiusculas() throws Exception {
        var navegador = new Navegador();

        navegador.entrar(novoUsuario().toUpperCase(), SENHA);

        assertThat(navegador.get("/teste/csrf").statusCode()).isEqualTo(200);
    }

    @Test
    void senhaErradaNaoEntra() throws Exception {
        assertLoginRecusado(novoUsuario(), "outra senha");
    }

    @Test
    void emailSemContaNaoEntra() throws Exception {
        assertLoginRecusado("ninguem@escola.test", SENHA);
    }

    @Test
    void usuarioInativoNaoEntraMesmoComASenhaCerta() throws Exception {
        String email = novoUsuario();
        jdbc.update("UPDATE usuario SET ativo = FALSE WHERE email = ?", email);

        assertLoginRecusado(email, SENHA);
    }

    @Test
    void loginTrocaOIdentificadorDaSessao() throws Exception {
        String email = novoUsuario();
        var navegador = new Navegador();
        navegador.get("/login");
        String antes = navegador.idDaSessao();

        navegador.entrar(email, SENHA);

        assertThat(navegador.idDaSessao()).isNotEqualTo(antes);
    }

    @Test
    void saidaEncerraASessao() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);
        String token = navegador.get("/teste/csrf").body();

        var saida = navegador.post("/logout", "", CABECALHO_CSRF, token);

        assertThat(saida.statusCode()).isEqualTo(302);
        assertThat(navegador.idDaSessao()).isNull();
        assertThat(destino(navegador.get("/teste/csrf"))).endsWith("/login");
    }

    // ---------------------------------------------------------------------------------------------
    // Senha
    // ---------------------------------------------------------------------------------------------

    @Test
    void senhaEGravadaSoComoHashArgon2() {
        String email = novoUsuario();

        String gravada = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE email = ?", String.class, email);

        assertThat(gravada).startsWith("$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain(SENHA);
    }

    @Test
    void mesmaSenhaGeraHashesDiferentes() {
        String primeiro = novoUsuario();
        String segundo = novoUsuario();

        String sql = "SELECT senha_hash FROM usuario WHERE email = ?";
        assertThat(jdbc.queryForObject(sql, String.class, primeiro))
                .isNotEqualTo(jdbc.queryForObject(sql, String.class, segundo));
    }

    @Test
    void cadastroExigeSenha() {
        assertThatThrownBy(() -> cadastro.cadastrar("Ana", "sem-senha@escola.test", " ", Set.of(Perfil.PROFESSOR)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cadastroRecusaEmailJaUsadoSemDiferenciarMaiusculas() {
        String email = novoUsuario();

        assertThatThrownBy(() -> cadastro.cadastrar("Ana", email.toUpperCase(), SENHA, Set.of(Perfil.PROFESSOR)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------------------------------------
    // Expiração da sessão
    // ---------------------------------------------------------------------------------------------

    @Test
    void sessaoUsaOTempoDeInatividadeConfigurado() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);

        assertThat(sessaoNoServidor(navegador).getMaxInactiveInterval()).isEqualTo(60 * 60);
    }

    @Test
    void sessaoExpiraDepoisDoTempoDeInatividade() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);

        expirar(navegador);

        var resposta = navegador.get("/teste/csrf");
        assertThat(resposta.statusCode()).isEqualTo(302);
        assertThat(destino(resposta)).endsWith("/login");
    }

    @Test
    void atividadeRenovaOTempoDaSessao() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);
        sessaoNoServidor(navegador).setMaxInactiveInterval(2);

        for (int i = 0; i < 3; i++) {
            Thread.sleep(1_000);
            assertThat(navegador.get("/teste/csrf").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void depoisDeExpirarDaParaEntrarDeNovo() throws Exception {
        String email = novoUsuario();
        var navegador = new Navegador();
        navegador.entrar(email, SENHA);
        expirar(navegador);

        navegador.entrar(email, SENHA);

        assertThat(navegador.get("/teste/csrf").statusCode()).isEqualTo(200);
    }

    @Test
    void requisicaoHtmxComSessaoExpiradaTrocaDePaginaEmVezDeReceberOLoginNoFragmento() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);
        expirar(navegador);

        assertRedirecionaPeloHtmx(navegador.get("/teste/csrf", HTMX, "true"));
    }

    @Test
    void envioHtmxComSessaoExpiradaTambemLevaAoLoginEmVezDeFalharPeloCsrf() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);
        String token = navegador.get("/teste/csrf").body();
        expirar(navegador);

        assertRedirecionaPeloHtmx(navegador.post("/teste/acao", "", HTMX, "true", CABECALHO_CSRF, token));
    }

    @Test
    void requisicaoHtmxSemSessaoLevaAoLogin() throws Exception {
        assertRedirecionaPeloHtmx(new Navegador().get("/teste/csrf", HTMX, "true"));
    }

    // ---------------------------------------------------------------------------------------------
    // CSRF
    // ---------------------------------------------------------------------------------------------

    @Test
    void envioSemTokenCsrfERecusado() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);

        assertThat(navegador.post("/teste/acao", "").statusCode()).isEqualTo(403);
    }

    /** O login limpa o token, então aqui a sessão é válida, mas ainda não tem token guardado. */
    @Test
    void envioHtmxLogoAposOLoginSemTokenCsrfERecusado() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);

        assertThat(navegador.post("/teste/acao", "", HTMX, "true").statusCode()).isEqualTo(403);
    }

    @Test
    void envioHtmxSemTokenCsrfDepoisDeCarregarUmaPaginaERecusado() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);
        navegador.get("/teste/csrf");

        assertThat(navegador.post("/teste/acao", "", HTMX, "true").statusCode()).isEqualTo(403);
    }

    @Test
    void envioHtmxComTokenCsrfInvalidoERecusado() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);
        navegador.get("/teste/csrf");

        var resposta = navegador.post("/teste/acao", "", HTMX, "true", CABECALHO_CSRF, "token-invalido");

        assertThat(resposta.statusCode()).isEqualTo(403);
    }

    @Test
    void envioHtmxComTokenCsrfNoCabecalhoEAceito() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);
        String token = navegador.get("/teste/csrf").body();

        var resposta = navegador.post("/teste/acao", "", HTMX, "true", CABECALHO_CSRF, token);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).isEqualTo("ok");
    }

    @Test
    void formularioComTokenCsrfNoCampoEAceito() throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(), SENHA);
        String token = navegador.get("/teste/csrf").body();

        var resposta = navegador.post("/teste/acao", "_csrf=" + codificar(token));

        assertThat(resposta.statusCode()).isEqualTo(200);
    }

    @Test
    void loginComTokenCsrfInvalidoRespondeProibidoSemEntrar() throws Exception {
        String email = novoUsuario();
        var navegador = new Navegador();
        navegador.get("/login");

        var resposta = navegador.post("/login", formulario(email, SENHA, "token-invalido"));

        assertThat(resposta.statusCode()).isEqualTo(403);
        assertThat(navegador.get("/teste/csrf").statusCode()).isEqualTo(302);
    }

    // ---------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------

    private String novoUsuario() {
        String email = "usuario" + SEQUENCIA.incrementAndGet() + "@escola.test";
        cadastro.cadastrar("Profissional de teste", email, SENHA, Set.of(Perfil.PROFESSOR));
        return email;
    }

    private void assertLoginRecusado(String email, String senha) throws Exception {
        var navegador = new Navegador();

        var resposta = navegador.tentarEntrar(email, senha);

        assertThat(destino(resposta)).endsWith("/login?error");
        assertThat(navegador.get("/teste/csrf").statusCode()).isEqualTo(302);
    }

    private static void assertRedirecionaPeloHtmx(HttpResponse<String> resposta) {
        assertThat(resposta.statusCode()).isEqualTo(401);
        assertThat(resposta.headers().firstValue("HX-Redirect")).hasValue("/login");
    }

    /** O tempo real é de uma hora, então a sessão passa a expirar em 1 s e o teste espera esse tempo. */
    private void expirar(Navegador navegador) throws InterruptedException, IOException {
        sessaoNoServidor(navegador).setMaxInactiveInterval(1);
        Thread.sleep(1_100);
    }

    private Session sessaoNoServidor(Navegador navegador) throws IOException {
        var servidor = (TomcatWebServer) contexto.getWebServer();
        var aplicacao = (Context) servidor.getTomcat().getHost().findChildren()[0];
        return aplicacao.getManager().findSession(navegador.idDaSessao());
    }

    private static String destino(HttpResponse<String> resposta) {
        return resposta.headers().firstValue("Location").orElse("");
    }

    private static String formulario(String email, String senha, String token) {
        return "email=" + codificar(email) + "&senha=" + codificar(senha) + "&_csrf=" + codificar(token);
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    /** Cliente HTTP que guarda os cookies e não segue redirecionamentos. */
    private final class Navegador {

        private final CookieManager cookies = new CookieManager();
        private final HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        void entrar(String email, String senha) throws IOException, InterruptedException {
            assertThat(destino(tentarEntrar(email, senha))).doesNotContain("/login");
        }

        HttpResponse<String> tentarEntrar(String email, String senha) throws IOException, InterruptedException {
            var pagina = get("/login");
            var token = CAMPO_CSRF.matcher(pagina.body());
            assertThat(token.find()).as("a tela de login traz o token CSRF").isTrue();
            return post("/login", formulario(email, senha, token.group(1)));
        }

        HttpResponse<String> get(String caminho, String... cabecalhos) throws IOException, InterruptedException {
            return enviar(requisicao(caminho, cabecalhos).GET());
        }

        HttpResponse<String> post(String caminho, String corpo, String... cabecalhos)
                throws IOException, InterruptedException {
            return enviar(requisicao(caminho, cabecalhos)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(BodyPublishers.ofString(corpo)));
        }

        String idDaSessao() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .map(java.net.HttpCookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        private HttpRequest.Builder requisicao(String caminho, String... cabecalhos) {
            var requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho));
            return cabecalhos.length == 0 ? requisicao : requisicao.headers(cabecalhos);
        }

        private HttpResponse<String> enviar(HttpRequest.Builder requisicao) throws IOException, InterruptedException {
            return cliente.send(requisicao.build(), BodyHandlers.ofString());
        }
    }
}
