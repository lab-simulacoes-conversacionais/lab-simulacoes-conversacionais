package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.ControlesDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.InicioDeSessao;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Critérios das issues 17 e 18 pela tela, com requisições HTTP reais: a pergunta vai pelo HTMX e a
 * resposta chega em frases moderadas pelo SSE (RF-16, RF-22), o conteúdo bloqueado nunca aparece
 * (RN-20) e o envio é bloqueado no limite com a sugestão do quiz (RN-18). O adaptador de IA, inclusive
 * a moderação, é simulado.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lab.teste.contexto=conversa-pela-tela")
@Import(InfraestruturaDeTeste.class)
class ConversaPelaTelaTest {

    private static final String SENHA = "uma senha de teste";
    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static final String LIMITE = "limite_perguntas_por_sessao";

    @LocalServerPort
    private int porta;

    @MockitoBean
    private AdaptadorDeIa adaptador;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private InicioDeSessao inicio;

    @Autowired
    private ControlesDaSessao controles;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private String professora;
    private long sessao;
    private Navegador navegador;

    @BeforeEach
    void entrarEIniciarSessao() throws Exception {
        dados = new DadosDeSessao(jdbc);
        professora = profissional(Perfil.PROFESSOR);
        sessao = comoProfessora(() -> inicio.iniciar(dados.turma(), cenario()).id());
        navegador = new Navegador();
        navegador.entrar(professora);
        when(adaptador.moderar(anyString())).thenReturn(ResultadoDaModeracao.aprovado());
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM parametro WHERE chave = ?", LIMITE);
    }

    /** RF-16: a pergunta volta no fragmento, que abre o canal SSE da resposta. */
    @Test
    void perguntaVoltaNoFragmentoQueAbreOCanal() throws Exception {
        var resposta = navegador.perguntar(sessao, "Quem é <você>?");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body())
                .contains("Quem é &lt;você&gt;?")
                .contains("sse-connect=\"/sessoes/" + sessao + "/resposta\"")
                .contains("sse-close=\"fim\"")
                .doesNotContain("passar ao quiz");
    }

    /** RF-16: as frases chegam escapadas, na ordem, e o canal termina com o evento que o fecha. */
    @Test
    void respostaChegaEmTrechosPeloSse() throws Exception {
        respondeEmTrechos("Olá, sou ", "Santos <b>Dumont</b>. Voei em ", "1906.");
        navegador.perguntar(sessao, "Quem é você?");

        var resposta = navegador.resposta(sessao);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        // O Spring Security manda este cabeçalho em toda resposta; nenhum proxy guarda o canal em cache.
        assertThat(resposta.headers().firstValue("Cache-Control").orElse("")).contains("no-cache").contains("no-store");
        assertThat(resposta.body())
                .containsSubsequence(
                        "event:trecho\ndata:Olá, sou Santos &lt;b&gt;Dumont&lt;/b&gt;. \n",
                        "event:trecho\ndata:Voei em 1906.\n",
                        "event:fim\n")
                .doesNotContain("event:falha")
                .doesNotContain("event:bloqueio");
        assertThat(jdbc.queryForObject(
                        "SELECT texto FROM mensagem WHERE sessao_id = ? AND autoria = 'IA'", String.class, sessao))
                .isEqualTo("Olá, sou Santos <b>Dumont</b>. Voei em 1906.");
    }

    /** A tela reconectou depois da resposta: o canal só fecha, sem chamar o modelo de novo. */
    @Test
    void canalSemPerguntaPendenteSoFecha() throws Exception {
        assertThat(navegador.resposta(sessao).body()).contains("event:fim").doesNotContain("event:trecho");
    }

    /** RNF-04: a falha do provedor vira um aviso neutro, e a pergunta pode ser respondida de novo. */
    @Test
    void falhaDoProvedorViraAvisoNeutro() throws Exception {
        when(adaptador.responder(any(), any())).thenThrow(new FalhaNoProvedorDeIa("HTTP 503 do provedor", null));
        navegador.perguntar(sessao, "Quem é você?");

        String corpo = navegador.resposta(sessao).body();

        assertThat(corpo)
                .containsSubsequence("event:falha\ndata:Não foi possível obter a resposta do personagem", "event:fim\n")
                .doesNotContain("503");
    }

    /**
     * RN-20: a frase bloqueada e as seguintes nunca chegam pelo SSE, e o evento de bloqueio traz só o
     * aviso neutro, sem o texto bloqueado. O canal fecha logo depois.
     */
    @Test
    void fraseBloqueadaNaoChegaPeloSse() throws Exception {
        respondeEmTrechos("Sou Santos Dumont. ", "Frase imprópria aqui. ", "Frase seguinte.");
        when(adaptador.moderar("Frase imprópria aqui.")).thenReturn(new ResultadoDaModeracao(true, List.of("hate")));
        navegador.perguntar(sessao, "Quem é você?");

        String corpo = navegador.resposta(sessao).body();

        assertThat(corpo)
                .containsSubsequence(
                        "event:trecho\ndata:Sou Santos Dumont. \n",
                        "event:bloqueio\ndata:" + ConversaController.AVISO_DE_BLOQUEIO + "\n",
                        "event:fim\n")
                .doesNotContain("imprópria")
                .doesNotContain("seguinte")
                .doesNotContain("hate")
                .doesNotContain("event:falha");
        assertThat(corpo.split("event:fim", -1)).hasSize(2);
    }

    /** RN-20: a pergunta bloqueada volta como aviso neutro, sem repetir o texto, e a sessão pausa. */
    @Test
    void perguntaBloqueadaViraAvisoNeutro() throws Exception {
        when(adaptador.moderar("Pergunta imprópria")).thenReturn(new ResultadoDaModeracao(true, List.of("violence")));

        var resposta = navegador.perguntar(sessao, "Pergunta imprópria");

        assertThat(resposta.statusCode()).isEqualTo(409);
        assertThat(resposta.headers().firstValue("HX-Retarget")).contains("#aviso-da-sessao");
        assertThat(resposta.body())
                .contains(ConversaController.AVISO_DE_PERGUNTA_BLOQUEADA)
                .doesNotContain("imprópria")
                .doesNotContain("sse-connect");
        assertThat(jdbc.queryForObject("SELECT motivo_pausa FROM sessao WHERE id = ?", String.class, sessao))
                .isEqualTo("CONTEUDO_BLOQUEADO");
    }

    /** A moderação da pergunta falhou: o professor vê um aviso para tentar de novo, sem detalhes internos. */
    @Test
    void falhaDaModeracaoDaPerguntaViraAviso() throws Exception {
        when(adaptador.moderar("Oi!")).thenThrow(new FalhaNoProvedorDeIa("HTTP 503 do provedor", null));

        var resposta = navegador.perguntar(sessao, "Oi!");

        assertThat(resposta.statusCode()).isEqualTo(503);
        assertThat(resposta.body()).contains("Tente de novo daqui a pouco").doesNotContain("HTTP 503");
    }

    /** A moderação da resposta falhou: o canal traz a falha e fecha, sem nenhuma frase. */
    @Test
    void falhaDaModeracaoDaRespostaViraFalhaNoSse() throws Exception {
        respondeEmTrechos("Sou Santos Dumont.");
        when(adaptador.moderar("Sou Santos Dumont.")).thenThrow(new FalhaNoProvedorDeIa("fora do ar", null));
        navegador.perguntar(sessao, "Quem é você?");

        assertThat(navegador.resposta(sessao).body())
                .containsSubsequence("event:falha\n", "event:fim\n")
                .doesNotContain("event:trecho")
                .doesNotContain("Santos");
    }

    /** RN-18: a última pergunta já sugere o quiz, e a seguinte é bloqueada com o aviso. */
    @Test
    void limiteDePerguntasBloqueiaOEnvioESugereOQuiz() throws Exception {
        jdbc.update("INSERT INTO parametro (chave, valor) VALUES (?, '1')", LIMITE);
        long curta = comoProfessora(() -> inicio.iniciar(dados.turma(), cenario()).id());
        respondeEmTrechos("Olá.");

        assertThat(navegador.perguntar(curta, "Oi!").body()).contains("Esta foi a última pergunta da sessão");
        navegador.resposta(curta);
        var bloqueada = navegador.perguntar(curta, "Mais uma?");

        assertThat(bloqueada.statusCode()).isEqualTo(409);
        assertThat(bloqueada.headers().firstValue("HX-Retarget")).contains("#aviso-da-sessao");
        assertThat(bloqueada.body()).contains("Que tal passar ao quiz?");
    }

    /** Um Enter repetido não deixa duas perguntas para uma resposta só. */
    @Test
    void segundaPerguntaAntesDaRespostaERecusada() throws Exception {
        navegador.perguntar(sessao, "Quem é você?");

        var repetida = navegador.perguntar(sessao, "Quem é você?");

        assertThat(repetida.statusCode()).isEqualTo(409);
        assertThat(repetida.headers().firstValue("HX-Retarget")).contains("#aviso-da-sessao");
        assertThat(repetida.body()).contains("A pergunta anterior ainda não foi respondida");
    }

    @Test
    void sessaoPausadaRecusaAPergunta() throws Exception {
        comoProfessora(() -> controles.pausar(sessao));

        var resposta = navegador.perguntar(sessao, "Oi!");

        assertThat(resposta.statusCode()).isEqualTo(409);
        assertThat(resposta.body()).contains("A sessão está pausada");
    }

    /** RN-19: sessão encerrada não volta; o aviso orienta a iniciar outra. */
    @Test
    void sessaoEncerradaRecusaAPergunta() throws Exception {
        comoProfessora(() -> controles.encerrar(sessao));

        var resposta = navegador.perguntar(sessao, "Oi!");

        assertThat(resposta.statusCode()).isEqualTo(409);
        assertThat(resposta.body()).contains("inicie uma nova sessão");
    }

    /** Um erro inesperado também fecha o canal com o aviso neutro, sem detalhes internos. */
    @Test
    void erroInesperadoTambemViraAvisoNeutro() throws Exception {
        when(adaptador.responder(any(), any())).thenThrow(new IllegalStateException("detalhe interno"));
        navegador.perguntar(sessao, "Quem é você?");

        assertThat(navegador.resposta(sessao).body())
                .containsSubsequence("event:falha\n", "event:fim\n")
                .doesNotContain("detalhe interno");
    }

    @Test
    void perguntaVaziaERecusada() throws Exception {
        var resposta = navegador.perguntar(sessao, "   ");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.body()).contains("Digite a pergunta da turma.");
    }

    @Test
    void sessaoInexistenteNaoEEncontrada() throws Exception {
        assertThat(navegador.perguntar(-1, "Oi!").statusCode()).isEqualTo(404);
        assertThat(navegador.resposta(-1).statusCode()).isEqualTo(404);
    }

    /** RN-10: outro professor não conversa nem ouve a sessão de quem a iniciou. */
    @Test
    void outroProfessorNaoUsaASessao() throws Exception {
        var outro = new Navegador();
        outro.entrar(profissional(Perfil.PROFESSOR));

        assertThat(outro.perguntar(sessao, "Oi!").statusCode()).isEqualTo(403);
        assertThat(outro.resposta(sessao).statusCode()).isEqualTo(403);
    }

    private void respondeEmTrechos(String... trechos) {
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            for (String trecho : trechos) {
                aoReceber.accept(trecho);
            }
            long metadados = jdbc.queryForObject(
                    "INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt) VALUES ('openai', 'm', 'v1') RETURNING id",
                    Long.class);
            return new RespostaDoPersonagem(String.join("", trechos), metadados);
        });
    }

    private String profissional(Perfil perfil) {
        String email = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Profissional", email, SENHA, Set.of(perfil));
        return email;
    }

    private long cenario() {
        return dados.cenarioAprovado(dados.idDe(dados.usuario("PROFESSOR")), dados.idDe(dados.usuario("CURADOR")))
                .cenarioId();
    }

    private <T> T comoProfessora(Supplier<T> acao) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(professora, null, "ROLE_PROFESSOR"));
        try {
            return acao.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Cliente HTTP que guarda os cookies, não segue redirecionamentos e manda o CSRF como o HTMX. */
    private final class Navegador {

        private final HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        private String tokenCsrf;

        void entrar(String email) throws IOException, InterruptedException {
            String corpo = "email=" + codificar(email) + "&senha=" + codificar(SENHA)
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

        private String csrfDe(String pagina) {
            Matcher token = CAMPO_CSRF.matcher(pagina);
            assertThat(token.find()).as("a página traz o token CSRF").isTrue();
            return token.group(1);
        }

        private String codificar(String valor) {
            return URLEncoder.encode(valor, StandardCharsets.UTF_8);
        }
    }
}
