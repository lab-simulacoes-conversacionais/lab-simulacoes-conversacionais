package br.ufg.inf.labsimulacoes.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.GestaoDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuario;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Critérios de aceite da issue 6 (RF-02, RF-03, RN-35, RN-37), com login HTTP real e banco real.
 * As rotas de teste fazem o papel das funções de cada perfil, que ainda não existem no sistema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({InfraestruturaDeTeste.class, PerfisTest.Rotas.class})
class PerfisTest {

    private static final String SENHA = "uma senha de teste";
    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String PROFESSOR = "/teste/professor";
    private static final String CURADOR = "/teste/curador";
    private static final String ADMINISTRADOR = "/teste/administrador";
    private static final String CONTEUDO_PEDAGOGICO = "/teste/conteudo-pedagogico";

    @LocalServerPort
    private int porta;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private GestaoDeUsuarios gestao;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @TestConfiguration(proxyBeanMethods = false)
    static class Rotas {

        @Bean
        RotasPorPerfil rotasPorPerfil() {
            return new RotasPorPerfil();
        }
    }

    @RestController
    static class RotasPorPerfil {

        @GetMapping(PROFESSOR)
        @PreAuthorize("hasRole('PROFESSOR')")
        String professor() {
            return "ok";
        }

        @GetMapping(CURADOR)
        @PreAuthorize("hasRole('CURADOR')")
        String curador() {
            return "ok";
        }

        @GetMapping(ADMINISTRADOR)
        @PreAuthorize("hasRole('ADMINISTRADOR')")
        String administrador() {
            return "ok";
        }

        /** Como os casos de uso de cenários vão declarar: RF-06 libera professor e curador. */
        @GetMapping(CONTEUDO_PEDAGOGICO)
        @PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
        String conteudoPedagogico() {
            return "ok";
        }
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------------------------------------
    // Critérios de aceite
    // ---------------------------------------------------------------------------------------------

    @Test
    void usuarioComDoisPerfisVeAsFuncoesDosDois() throws Exception {
        var navegador = logado(Perfil.PROFESSOR, Perfil.CURADOR);

        assertThat(navegador.status(PROFESSOR)).isEqualTo(200);
        assertThat(navegador.status(CURADOR)).isEqualTo(200);
        assertThat(navegador.status(ADMINISTRADOR)).isEqualTo(403);
    }

    @Test
    void usuarioComUmPerfilNaoVeAsFuncoesDosOutros() throws Exception {
        var navegador = logado(Perfil.PROFESSOR);

        assertThat(navegador.status(PROFESSOR)).isEqualTo(200);
        assertThat(navegador.status(CURADOR)).isEqualTo(403);
    }

    @Test
    void administradorNaoEditaConteudoPedagogico() throws Exception {
        var navegador = logado(Perfil.ADMINISTRADOR);

        assertThat(navegador.status(ADMINISTRADOR)).isEqualTo(200);
        assertThat(navegador.status(CONTEUDO_PEDAGOGICO)).isEqualTo(403);
    }

    @Test
    void alteracaoDePerfilApareceNoLogComAutorEDetalhes() throws Exception {
        Usuario admin = novoUsuario(Perfil.ADMINISTRADOR);
        Usuario alvo = novoUsuario(Perfil.PROFESSOR);
        comoUsuario(admin, Perfil.ADMINISTRADOR);

        gestao.atribuirPerfis(alvo.id(), Set.of(Perfil.PROFESSOR, Perfil.CURADOR));

        Map<String, Object> linha = aguardarLog(alvo.id()).get(0);
        assertThat(linha.get("usuario_id")).isEqualTo(admin.id());
        assertThat(linha.get("entidade")).isEqualTo("usuario");
        assertThat(linha.get("ocorrido_em")).isNotNull();
        JsonNode detalhes = json.readTree(linha.get("detalhes").toString());
        assertThat(detalhes.get("perfisAnteriores").toString()).isEqualTo("[\"PROFESSOR\"]");
        assertThat(detalhes.get("perfisNovos").toString()).isEqualTo("[\"CURADOR\",\"PROFESSOR\"]");
    }

    @Test
    void cadastroPeloAdministradorTambemApareceNoLog() throws Exception {
        Usuario admin = novoUsuario(Perfil.ADMINISTRADOR);
        comoUsuario(admin, Perfil.ADMINISTRADOR);

        Usuario novo = gestao.cadastrar("Nova", novoEmail(), SENHA, Set.of(Perfil.CURADOR));

        assertThat(aguardarLog(novo.id()).get(0).get("usuario_id")).isEqualTo(admin.id());
    }

    // ---------------------------------------------------------------------------------------------
    // Autorização da gestão de usuários
    // ---------------------------------------------------------------------------------------------

    @Test
    void soAdministradorUsaAGestaoDeUsuarios() {
        Usuario professor = novoUsuario(Perfil.PROFESSOR);
        comoUsuario(professor, Perfil.PROFESSOR, Perfil.CURADOR);
        long id = professor.id();

        assertThatThrownBy(gestao::listar).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> gestao.buscar(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> gestao.cadastrar("Ana", novoEmail(), SENHA, Set.of(Perfil.PROFESSOR)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> gestao.ativar(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> gestao.desativar(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> gestao.atribuirPerfis(id, Set.of(Perfil.ADMINISTRADOR)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semLoginAGestaoDeUsuariosERecusada() {
        assertThatThrownBy(gestao::listar).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    // ---------------------------------------------------------------------------------------------
    // Efeito sobre quem já está logado
    // ---------------------------------------------------------------------------------------------

    @Test
    void perfilAtribuidoValeSemPrecisarEntrarDeNovo() throws Exception {
        Usuario usuario = novoUsuario(Perfil.PROFESSOR);
        var navegador = new Navegador();
        navegador.entrar(usuario.email());
        assertThat(navegador.status(CURADOR)).isEqualTo(403);

        comoAdministrador();
        gestao.atribuirPerfis(usuario.id(), Set.of(Perfil.CURADOR));

        assertThat(navegador.status(CURADOR)).isEqualTo(200);
        assertThat(navegador.status(PROFESSOR)).isEqualTo(403);
    }

    @Test
    void usuarioDesativadoPerdeOAcessoNaRequisicaoSeguinte() throws Exception {
        Usuario usuario = novoUsuario(Perfil.PROFESSOR);
        var navegador = new Navegador();
        navegador.entrar(usuario.email());
        assertThat(navegador.status(PROFESSOR)).isEqualTo(200);

        comoAdministrador();
        gestao.desativar(usuario.id());

        var resposta = navegador.get(PROFESSOR);
        assertThat(resposta.statusCode()).isEqualTo(302);
        assertThat(resposta.headers().firstValue("Location").orElse("")).endsWith("/login");
        assertThat(navegador.tentarEntrar(usuario.email()).headers().firstValue("Location").orElse(""))
                .endsWith("/login?error");
    }

    @Test
    void usuarioReativadoVoltaAEntrar() throws Exception {
        Usuario usuario = novoUsuario(Perfil.PROFESSOR);
        comoAdministrador();
        gestao.desativar(usuario.id());

        gestao.ativar(usuario.id());

        var navegador = new Navegador();
        navegador.entrar(usuario.email());
        assertThat(navegador.status(PROFESSOR)).isEqualTo(200);
    }

    // ---------------------------------------------------------------------------------------------
    // Persistência
    // ---------------------------------------------------------------------------------------------

    @Test
    void perfisFicamNaTabelaDeLigacaoESubstituemOsAnteriores() {
        Usuario usuario = novoUsuario(Perfil.PROFESSOR, Perfil.CURADOR);
        comoAdministrador();

        gestao.atribuirPerfis(usuario.id(), Set.of(Perfil.CURADOR, Perfil.ADMINISTRADOR));

        assertThat(jdbc.queryForList(
                        "SELECT perfil FROM usuario_perfil WHERE usuario_id = ?", String.class, usuario.id()))
                .containsExactlyInAnyOrder("CURADOR", "ADMINISTRADOR");
        assertThat(gestao.buscar(usuario.id()).perfis())
                .containsExactlyInAnyOrder(Perfil.CURADOR, Perfil.ADMINISTRADOR);
    }

    @Test
    void listaTrazOsUsuariosCadastradosEmOrdemDeNome() {
        Usuario usuario = novoUsuario(Perfil.PROFESSOR);
        comoAdministrador();

        List<Usuario> lista = gestao.listar();

        assertThat(lista).extracting(Usuario::id).contains(usuario.id());
        assertThat(lista).extracting(Usuario::nome).isSorted();
    }

    // ---------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------

    private String novoEmail() {
        return "perfis" + SEQUENCIA.incrementAndGet() + "@escola.test";
    }

    private Usuario novoUsuario(Perfil... perfis) {
        return cadastro.cadastrar("Profissional", novoEmail(), SENHA, Set.of(perfis));
    }

    private Navegador logado(Perfil... perfis) throws Exception {
        var navegador = new Navegador();
        navegador.entrar(novoUsuario(perfis).email());
        return navegador;
    }

    private void comoAdministrador() {
        comoUsuario(novoUsuario(Perfil.ADMINISTRADOR), Perfil.ADMINISTRADOR);
    }

    private static void comoUsuario(Usuario usuario, Perfil... perfis) {
        String[] papeis = java.util.Arrays.stream(perfis).map(perfil -> "ROLE_" + perfil).toArray(String[]::new);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(usuario.email(), null, papeis));
    }

    /** O log é gravado depois da transação, em outra thread, como no sistema real. */
    private List<Map<String, Object>> aguardarLog(long usuarioId) throws InterruptedException {
        List<Map<String, Object>> linhas = List.of();
        for (int tentativa = 0; tentativa < 100 && linhas.isEmpty(); tentativa++) {
            Thread.sleep(50);
            linhas = jdbc.queryForList("""
                    SELECT usuario_id, entidade, detalhes::text AS detalhes, ocorrido_em
                    FROM log_auditoria WHERE acao = 'ALTERAR_PERFIS' AND entidade_id = ?""",
                    String.valueOf(usuarioId));
        }
        assertThat(linhas).as("linha do log de auditoria").hasSize(1);
        return linhas;
    }

    /** Cliente HTTP que guarda os cookies e não segue redirecionamentos. */
    private final class Navegador {

        private final HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        void entrar(String email) throws IOException, InterruptedException {
            assertThat(tentarEntrar(email).headers().firstValue("Location").orElse("")).doesNotContain("/login");
        }

        HttpResponse<String> tentarEntrar(String email) throws IOException, InterruptedException {
            var token = CAMPO_CSRF.matcher(get("/login").body());
            assertThat(token.find()).isTrue();
            String corpo = "email=" + codificar(email) + "&senha=" + codificar(SENHA) + "&_csrf=" + codificar(token.group(1));
            return cliente.send(
                    requisicao("/login")
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(BodyPublishers.ofString(corpo))
                            .build(),
                    BodyHandlers.ofString());
        }

        int status(String caminho) throws IOException, InterruptedException {
            return get(caminho).statusCode();
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
