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
 * Critérios de aceite da issue 21: telas de administração de usuários, turmas e parâmetros
 * (RF-02, RF-03, RF-04, RN-37). Requisições HTTP reais, como nas demais telas protegidas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InfraestruturaDeTeste.class)
class AdministracaoTest {

    private static final String SENHA = "uma senha de teste";
    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @LocalServerPort
    private int porta;

    @Autowired
    private CadastroDeUsuarios cadastro;

    // ---------------------------------------------------------------------------------------------
    // Usuários
    // ---------------------------------------------------------------------------------------------

    @Test
    void administradorVeAListaDeUsuarios() throws Exception {
        String admin = novoAdministrador();
        var navegador = logado(admin);

        var resposta = navegador.get("/admin/usuarios");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains(admin).contains("Usuários profissionais");
    }

    @Test
    void administradorCadastraUsuario() throws Exception {
        var navegador = logado(novoAdministrador());
        String email = "nova.professora" + SEQUENCIA.incrementAndGet() + "@escola.test";

        var resposta = navegador.postForm("/admin/usuarios",
                "nome=Nova+Professora&email=" + cod(email) + "&senha=" + cod(SENHA) + "&perfis=PROFESSOR");

        assertThat(local(resposta)).endsWith("/admin/usuarios");
        assertThat(navegador.get("/admin/usuarios").body()).contains(email).contains("Nova Professora");
    }

    @Test
    void cadastroComEmailDuplicadoMostraErro() throws Exception {
        var navegador = logado(novoAdministrador());
        String email = "repetida" + SEQUENCIA.incrementAndGet() + "@escola.test";
        String corpo = "nome=Alguem&email=" + cod(email) + "&senha=" + cod(SENHA) + "&perfis=CURADOR";
        navegador.postForm("/admin/usuarios", corpo);

        var resposta = navegador.postForm("/admin/usuarios", corpo);

        assertThat(local(resposta)).endsWith("/admin/usuarios/novo");
        assertThat(navegador.get("/admin/usuarios/novo").body()).contains("Já existe um usuário com este e-mail");
    }

    @Test
    void administradorDesativaEReativaUsuario() throws Exception {
        var navegador = logado(novoAdministrador());
        long id = idDoUsuario(novoProfessor());

        navegador.postForm("/admin/usuarios/" + id + "/desativar", "");
        assertThat(navegador.get("/admin/usuarios").body()).contains("Desativado");

        navegador.postForm("/admin/usuarios/" + id + "/ativar", "");
        assertThat(navegador.get("/admin/usuarios").body()).doesNotContain("Desativado");
    }

    @Test
    void administradorAtribuiPerfisAcumulaveis() throws Exception {
        var navegador = logado(novoAdministrador());
        long id = idDoUsuario(novoProfessor());

        navegador.postForm("/admin/usuarios/" + id + "/perfis", "perfis=PROFESSOR&perfis=CURADOR");

        assertThat(navegador.get("/admin/usuarios").body())
                .contains("value=\"PROFESSOR\" checked")
                .contains("value=\"CURADOR\" checked");
    }

    // ---------------------------------------------------------------------------------------------
    // Turmas
    // ---------------------------------------------------------------------------------------------

    @Test
    void administradorCadastraEditaEExcluiTurma() throws Exception {
        var navegador = logado(novoAdministrador());
        String rotulo = "8º " + (char) ('A' + SEQUENCIA.incrementAndGet() % 20);

        navegador.postForm("/admin/turmas", turma("EMEF Horizonte", "8º ano", "Manhã", rotulo));
        assertThat(navegador.get("/admin/turmas").body()).contains(rotulo).contains("EMEF Horizonte");

        long id = idDaTurma(navegador, rotulo);
        assertThat(navegador.get("/admin/turmas/" + id + "/editar").body()).contains("EMEF Horizonte").contains(rotulo);

        navegador.postForm("/admin/turmas/" + id, turma("EMEF Caminhos", "9º ano", "Tarde", rotulo));
        assertThat(navegador.get("/admin/turmas").body()).contains("EMEF Caminhos");

        navegador.postForm("/admin/turmas/" + id + "/excluir", "");
        assertThat(navegador.get("/admin/turmas").body()).doesNotContain("EMEF Caminhos");
    }

    @Test
    void turmaDuplicadaMostraErro() throws Exception {
        var navegador = logado(novoAdministrador());
        String rotulo = "7º Z" + SEQUENCIA.incrementAndGet();
        String corpo = turma("EMEF Horizonte", "7º ano", "Noite", rotulo);
        navegador.postForm("/admin/turmas", corpo);

        var resposta = navegador.postForm("/admin/turmas", corpo);

        assertThat(local(resposta)).endsWith("/admin/turmas/nova");
        assertThat(navegador.get("/admin/turmas/nova").body()).contains("Já existe a turma");
    }

    @Test
    void turmaInexistenteVoltaParaALista() throws Exception {
        var navegador = logado(novoAdministrador());

        var resposta = navegador.get("/admin/turmas/999999/editar");

        assertThat(resposta.statusCode()).isEqualTo(302);
        assertThat(local(resposta)).endsWith("/admin/turmas");
    }

    // ---------------------------------------------------------------------------------------------
    // Parâmetros
    // ---------------------------------------------------------------------------------------------

    @Test
    void administradorSalvaParametros() throws Exception {
        var navegador = logado(novoAdministrador());

        navegador.postForm("/admin/parametros", "limiteDePerguntasPorSessao=33&custoDiarioMaximoUsd=120");

        assertThat(navegador.get("/admin/parametros").body())
                .contains("value=\"33\"")
                .contains("value=\"120\"");
    }

    // ---------------------------------------------------------------------------------------------
    // Autorização
    // ---------------------------------------------------------------------------------------------

    @Test
    void professorNaoAcessaAAdministracao() throws Exception {
        var navegador = logado(novoProfessor());

        assertThat(navegador.get("/admin/usuarios").statusCode()).isEqualTo(403);
        assertThat(navegador.get("/admin/turmas").statusCode()).isEqualTo(403);
        assertThat(navegador.get("/admin/parametros").statusCode()).isEqualTo(403);
    }

    // ---------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------

    private String novoAdministrador() {
        String email = "admin" + SEQUENCIA.incrementAndGet() + "@escola.test";
        cadastro.cadastrar("Administrador", email, SENHA, Set.of(Perfil.ADMINISTRADOR));
        return email;
    }

    private String novoProfessor() {
        String email = "prof" + SEQUENCIA.incrementAndGet() + "@escola.test";
        cadastro.cadastrar("Professor", email, SENHA, Set.of(Perfil.PROFESSOR));
        return email;
    }

    private long idDoUsuario(String email) throws IOException, InterruptedException {
        // O id não aparece como texto; o teste o extrai da ação do formulário da linha daquele e-mail.
        var navegador = logado(novoAdministrador());
        var corpo = navegador.get("/admin/usuarios").body();
        var linha = Pattern.compile("(\\d+)/perfis");
        for (String parte : corpo.split("<tr")) {
            if (parte.contains(email)) {
                var m = linha.matcher(parte);
                if (m.find()) {
                    return Long.parseLong(m.group(1));
                }
            }
        }
        throw new IllegalStateException("não achei o id do usuário " + email);
    }

    private long idDaTurma(Navegador navegador, String rotulo) throws IOException, InterruptedException {
        var corpo = navegador.get("/admin/turmas").body();
        var linha = Pattern.compile("/admin/turmas/(\\d+)/editar");
        for (String parte : corpo.split("<tr")) {
            if (parte.contains(rotulo)) {
                var m = linha.matcher(parte);
                if (m.find()) {
                    return Long.parseLong(m.group(1));
                }
            }
        }
        throw new IllegalStateException("não achei o id da turma " + rotulo);
    }

    private static String turma(String escola, String ano, String turno, String rotulo) {
        return "escola=" + cod(escola) + "&anoSerie=" + cod(ano) + "&turno=" + cod(turno) + "&rotulo=" + cod(rotulo);
    }

    private Navegador logado(String email) {
        try {
            var navegador = new Navegador();
            navegador.entrar(email);
            return navegador;
        } catch (IOException | InterruptedException erro) {
            throw new IllegalStateException(erro);
        }
    }

    private static String local(HttpResponse<String> resposta) {
        return resposta.headers().firstValue("Location").orElse("");
    }

    private static String cod(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    /** Cliente HTTP que guarda os cookies, não segue redirecionamentos e reaproveita o token CSRF. */
    private final class Navegador {

        private final HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        private String csrf;

        void entrar(String email) throws IOException, InterruptedException {
            String token = tokenDe("/login");
            var resposta = postBruto("/login",
                    "email=" + cod(email) + "&senha=" + cod(SENHA) + "&_csrf=" + cod(token));
            assertThat(local(resposta)).doesNotContain("/login");
            csrf = tokenDe("/login");
        }

        HttpResponse<String> get(String caminho) throws IOException, InterruptedException {
            return cliente.send(requisicao(caminho).GET().build(), BodyHandlers.ofString());
        }

        HttpResponse<String> postForm(String caminho, String corpo) throws IOException, InterruptedException {
            String completo = corpo.isEmpty() ? "_csrf=" + cod(csrf) : corpo + "&_csrf=" + cod(csrf);
            return postBruto(caminho, completo);
        }

        private HttpResponse<String> postBruto(String caminho, String corpo)
                throws IOException, InterruptedException {
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
}






