package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A revisão da conversa e a sinalização pela tela (RF-23, RN-21): o professor vê só as próprias sessões,
 * sinaliza dentro do prazo e recebe as recusas no lugar do formulário, com status 200, como na curadoria.
 * A sessão de outro professor responde 404. Textos e dados inventados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InfraestruturaDeTeste.class)
class SinalizacaoPelaTelaTest {

    private static final String SENHA = "uma senha de teste";
    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final String PERGUNTA = "Quando foi o primeiro voo?";
    private static final String RESPOSTA = "Voei em 1901, em Paris.";
    private static final String JUSTIFICATIVA = "O voo homologado foi em 23 de outubro de 1906.";

    @LocalServerPort
    private int porta;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private String professora;
    private long professoraId;
    private long versao;

    @BeforeEach
    void prepararCenario() {
        dados = new DadosDeSessao(jdbc);
        professora = professor();
        professoraId = dados.idDe(professora);
        versao = dados.cenarioAprovado(professoraId, dados.idDe(dados.usuario("CURADOR"))).versaoId();
    }

    /** RN-37: a sessão de outro professor é como uma que não existe, e a conversa não aparece. */
    @Test
    void revisaoDaSessaoDeOutroProfessorResponde404() throws Exception {
        long sessao = sessaoEncerrada(professoraId, Duration.ofHours(2));
        resposta(sessao);
        var outro = entrar(professor());

        var pagina = outro.get("/sessoes/" + sessao + "/revisao");

        assertThat(pagina.statusCode()).isEqualTo(404);
        assertThat(pagina.body())
                .contains("Sessão não encontrada")
                .doesNotContain(PERGUNTA)
                .doesNotContain(RESPOSTA)
                .doesNotContain("Sinalizar possível erro");
    }

    @Test
    void sessaoQueNaoExisteResponde404() throws Exception {
        var navegador = entrar(professora);

        assertThat(navegador.get("/sessoes/" + Long.MAX_VALUE + "/revisao").statusCode()).isEqualTo(404);
    }

    @Test
    void outroProfessorNaoSinalizaPelaTela() throws Exception {
        long sessao = sessaoEncerrada(professoraId, Duration.ofHours(2));
        long resposta = resposta(sessao);
        var outro = entrar(professor());

        var envio = outro.postHtmx(caminhoDaSinalizacao(sessao, resposta), formulario("ERRO_HISTORICO", "LEVE", JUSTIFICATIVA));

        assertThat(envio.statusCode()).isEqualTo(200);
        assertThat(envio.body()).contains("Resposta não encontrada.").doesNotContain(RESPOSTA);
        assertThat(sinalizacoesDa(resposta)).isZero();
    }

    @Test
    void donaVeAConversaEOBotaoDeSinalizarSoNasRespostas() throws Exception {
        long sessao = sessaoEncerrada(professoraId, Duration.ofHours(2));
        long resposta = resposta(sessao);
        var navegador = entrar(professora);

        var pagina = navegador.get("/sessoes/" + sessao + "/revisao");

        assertThat(pagina.statusCode()).isEqualTo(200);
        assertThat(pagina.body())
                .contains(PERGUNTA, RESPOSTA, "O voo do 14-bis", "O professor pode sinalizar até")
                .contains("id=\"sinalizar-" + resposta + "\"")
                .contains(caminhoDaSinalizacao(sessao, resposta))
                .contains("Erro histórico", "Saída do personagem", "Grave", "maxlength=\"500\"");
        assertThat(pagina.body().split("Sinalizar possível erro", -1)).hasSize(2);
    }

    @Test
    void sinalizarPelaTelaGravaComoProfessoraEMostraAConfirmacao() throws Exception {
        long sessao = sessaoEncerrada(professoraId, Duration.ofHours(2));
        long resposta = resposta(sessao);
        var navegador = entrar(professora);

        var envio = navegador.postHtmx(
                caminhoDaSinalizacao(sessao, resposta), formulario("ANACRONISMO", "GRAVE", JUSTIFICATIVA));

        assertThat(envio.statusCode()).isEqualTo(200);
        assertThat(envio.body())
                .contains("Sinalização enviada. A curadoria vai analisar a resposta.")
                .doesNotContain("<form");
        assertThat(jdbc.queryForMap(
                        "SELECT perfil, tipo, gravidade_percebida, justificativa FROM sinalizacao WHERE mensagem_id = ?",
                        resposta))
                .containsEntry("perfil", "PROFESSOR")
                .containsEntry("tipo", "ANACRONISMO")
                .containsEntry("gravidade_percebida", "GRAVE")
                .containsEntry("justificativa", JUSTIFICATIVA);

        var depois = navegador.get("/sessoes/" + sessao + "/revisao").body();
        assertThat(depois).contains("Você sinalizou esta resposta").doesNotContain("Sinalizar possível erro");
    }

    @Test
    void recusasVoltamNoLugarDoFormularioComStatus200() throws Exception {
        long sessao = sessaoEncerrada(professoraId, Duration.ofHours(2));
        long resposta = resposta(sessao);
        var navegador = entrar(professora);
        String caminho = caminhoDaSinalizacao(sessao, resposta);

        var semTipo = navegador.postHtmx(caminho, formulario(null, "LEVE", JUSTIFICATIVA));
        assertThat(semTipo.statusCode()).isEqualTo(200);
        assertThat(semTipo.body()).contains("Escolha o tipo do erro").contains("<form").contains(JUSTIFICATIVA);

        var tipoInventado = navegador.postHtmx(caminho, formulario("ERRO_DE_DIGITACAO", "LEVE", JUSTIFICATIVA));
        assertThat(tipoInventado.body()).contains("Escolha o tipo do erro");

        var comTelefone = navegador.postHtmx(caminho, formulario("ERRO_HISTORICO", "LEVE", "Liguem no 98765-4321"));
        assertThat(comTelefone.statusCode()).isEqualTo(200);
        assertThat(comTelefone.body())
                .contains("A justificativa parece ter um dado pessoal (telefone).")
                .doesNotContain("98765");
        assertThat(sinalizacoesDa(resposta)).isZero();

        navegador.postHtmx(caminho, formulario("ERRO_HISTORICO", "LEVE", JUSTIFICATIVA));
        var repetida = navegador.postHtmx(caminho, formulario("ERRO_HISTORICO", "LEVE", JUSTIFICATIVA));
        assertThat(repetida.statusCode()).isEqualTo(200);
        assertThat(repetida.body()).contains("Você já tinha sinalizado esta resposta.").doesNotContain("<form");
        assertThat(sinalizacoesDa(resposta)).isEqualTo(1);
    }

    /** Depois dos 7 dias, a conversa fica só para leitura e a sinalização é recusada (RN-21). */
    @Test
    void foraDoPrazoAConversaFicaSoParaLeitura() throws Exception {
        long sessao = sessaoEncerrada(professoraId, Duration.ofDays(8));
        long resposta = resposta(sessao);
        var navegador = entrar(professora);

        var pagina = navegador.get("/sessoes/" + sessao + "/revisao");
        var envio = navegador.postHtmx(caminhoDaSinalizacao(sessao, resposta), formulario("ERRO_HISTORICO", "LEVE", JUSTIFICATIVA));

        assertThat(pagina.statusCode()).isEqualTo(200);
        assertThat(pagina.body()).contains(RESPOSTA, "O prazo para sinalizar terminou em").doesNotContain("Sinalizar possível erro");
        assertThat(envio.body()).contains("O prazo para sinalizar terminou em").doesNotContain("<form");
        assertThat(sinalizacoesDa(resposta)).isZero();
    }

    @Test
    void sessoesRecentesMostramSoAsDaProfessoraNoPrazo() throws Exception {
        long recente = sessaoEncerrada(professoraId, Duration.ofHours(2));
        long antiga = sessaoEncerrada(professoraId, Duration.ofDays(8));
        long deOutro = sessaoEncerrada(dados.idDe(professor()), Duration.ofHours(1));
        var navegador = entrar(professora);

        var pagina = navegador.get("/sessoes/revisao");

        assertThat(pagina.statusCode()).isEqualTo(200);
        assertThat(pagina.body())
                .contains("/sessoes/" + recente + "/revisao", "Pode sinalizar até", "Encerrada")
                .doesNotContain("/sessoes/" + antiga + "/revisao")
                .doesNotContain("/sessoes/" + deOutro + "/revisao");
    }

    @Test
    void telasDaSessaoLevamARevisao() throws Exception {
        long sessao = sessaoEncerrada(professoraId, Duration.ofHours(2));
        var navegador = entrar(professora);

        assertThat(navegador.get("/sessoes/" + sessao + "/encerrada").body())
                .contains("href=\"revisao\"", "Revisar a conversa");
        assertThat(navegador.get("/sessoes/nova").body()).contains("href=\"/sessoes/revisao\"", "Sessões recentes");
    }

    /**
     * As rotas de /sessoes são só do professor (ConfiguracaoDeSeguranca): o curador sem o perfil de
     * professor ainda não tem tela de sinalização, que fica para a #44 ou outra issue.
     */
    @Test
    void curadorSemPerfilDeProfessorNaoAbreARevisao() throws Exception {
        long sessao = sessaoEncerrada(professoraId, Duration.ofHours(2));
        String curador = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Curadoria", curador, SENHA, Set.of(Perfil.CURADOR));
        var navegador = entrar(curador);

        assertThat(navegador.get("/sessoes/" + sessao + "/revisao").statusCode()).isEqualTo(403);
    }

    private String professor() {
        String email = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Profissional", email, SENHA, Set.of(Perfil.PROFESSOR));
        return email;
    }

    private Navegador entrar(String email) throws Exception {
        var navegador = new Navegador(porta);
        navegador.entrar(email, SENHA);
        return navegador;
    }

    private long sessaoEncerrada(long professorId, Duration haQuantoTempo) {
        Instant fim = AGORA.minus(haQuantoTempo);
        return jdbc.queryForObject(
                """
                INSERT INTO sessao (professor_id, turma_id, cenario_versao_id, limite_perguntas, status, iniciada_em,
                                    encerrada_em)
                VALUES (?, ?, ?, 40, 'ENCERRADA', ?, ?) RETURNING id""",
                Long.class,
                professorId, dados.turma(), versao,
                fim.minus(Duration.ofMinutes(40)).atOffset(ZoneOffset.UTC), fim.atOffset(ZoneOffset.UTC));
    }

    private long resposta(long sessao) {
        Instant inicio = jdbc.queryForObject("SELECT iniciada_em FROM sessao WHERE id = ?", java.sql.Timestamp.class, sessao)
                .toInstant();
        dados.mensagem(sessao, "TURMA", PERGUNTA, inicio.plusSeconds(60));
        return jdbc.queryForObject(
                "INSERT INTO mensagem (sessao_id, autoria, texto, criado_em) VALUES (?, 'IA', ?, ?) RETURNING id",
                Long.class, sessao, RESPOSTA, inicio.plusSeconds(70).atOffset(ZoneOffset.UTC));
    }

    private long sinalizacoesDa(long resposta) {
        return jdbc.queryForObject("SELECT count(*) FROM sinalizacao WHERE mensagem_id = ?", Long.class, resposta);
    }

    private static String caminhoDaSinalizacao(long sessao, long resposta) {
        return "/sessoes/" + sessao + "/revisao/respostas/" + resposta + "/sinalizacao";
    }

    private static String formulario(String tipo, String gravidade, String justificativa) {
        StringBuilder corpo = new StringBuilder("justificativa=").append(codificar(justificativa));
        if (tipo != null) {
            corpo.append("&tipo=").append(codificar(tipo));
        }
        corpo.append("&gravidade=").append(codificar(gravidade));
        return corpo.toString();
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
