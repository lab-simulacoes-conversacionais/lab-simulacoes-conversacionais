package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Critérios de aceite da issue 44 na fila de triagem, pela tela e com requisições HTTP reais: a aba
 * "Sinalizações" da fila do curador, com o prazo de 2 dias úteis e as atrasadas em destaque (RF-24, RN-24),
 * a confirmação com a gravidade final (RN-22), o descarte e o destaque das atrasadas para o administrador,
 * sem os textos (RN-37). Textos inventados.
 *
 * <p>As telas leem o banco de verdade, e outros testes deixam sinalizações pendentes. Por isso cada teste
 * começa descartando as pendentes que encontrar, para a fila mostrar só as dele.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InfraestruturaDeTeste.class)
class TriagemPelaTelaTest {

    private static final String SENHA = "uma senha de teste";
    private static final String CAMINHO = "/curadoria/sinalizacoes";
    private static final String PERGUNTA = "Quando foi o primeiro voo?";
    private static final String RESPOSTA = "Voei em 1901, em Paris.";

    @LocalServerPort
    private int porta;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private JdbcTemplate jdbc;

    private Navegador curadora;
    private long curadoraId;
    private long professorId;
    private long sessaoId;
    private long versaoId;

    @BeforeEach
    void prepararFila() throws Exception {
        var dados = new DadosDeSessao(jdbc);
        professorId = dados.idDe(dados.usuario("PROFESSOR"));
        versaoId = dados.cenarioAprovado(professorId, dados.idDe(dados.usuario("CURADOR"))).versaoId();
        Instant inicio = Instant.now().minus(Duration.ofDays(20));
        sessaoId = jdbc.queryForObject(
                """
                INSERT INTO sessao (professor_id, turma_id, cenario_versao_id, limite_perguntas, status, iniciada_em,
                                    encerrada_em)
                VALUES (?, ?, ?, 40, 'ENCERRADA', ?, ?) RETURNING id""",
                Long.class, professorId, dados.turma(), versaoId, inicio.atOffset(ZoneOffset.UTC),
                inicio.plusSeconds(3000).atOffset(ZoneOffset.UTC));
        String email = UUID.randomUUID() + "@escola.test";
        curadoraId = cadastro.cadastrar("Curadora da Triagem", email, SENHA, Set.of(Perfil.CURADOR)).id();
        jdbc.update("""
                UPDATE sinalizacao SET status = 'DESCARTADA', triada_por_id = ?, triada_em = now()
                WHERE status = 'PENDENTE'""", curadoraId);
        curadora = Navegador.logado(porta, email, SENHA);
    }

    // ---------------------------------------------------------------------------------------------
    // A fila
    // ---------------------------------------------------------------------------------------------

    /** Como no protótipo: as abas, a nota do prazo e a tabela, com a atrasada em destaque e primeiro. */
    @Test
    void filaMostraAsAbasOPrazoEAAtrasadaEmDestaque() throws Exception {
        long atrasada = pendente(Instant.now().minus(Duration.ofDays(10)), "GRAVE", "Justificativa da atrasada.");
        long noPrazo = pendente(Instant.now().minus(Duration.ofMinutes(5)), "LEVE", "Justificativa no prazo.");

        var pagina = curadora.get(CAMINHO);

        assertThat(pagina.statusCode()).isEqualTo(200);
        String corpo = pagina.body();
        assertThat(corpo)
                .contains("Fila do Curador")
                .containsPattern("<a href=\"/curadoria\"[^>]*>Cenários")
                .containsPattern("<a href=\"/curadoria/sinalizacoes\" aria-current=\"page\" class=\"active\">Sinalizações\\s*<span>2</span>")
                .contains("hx-get=\"/curadoria/contagem\"")
                .contains("Prazo de tratamento: 2 dias úteis")
                .contains("<strong>1</strong> atrasada")
                .contains("O voo do 14-bis").contains("Erro histórico")
                .contains("! Grave").contains("! Leve")
                .contains("pelo professor");
        String linhaAtrasada = linhaDe(corpo, atrasada);
        assertThat(linhaAtrasada).contains("overdue-row").contains("Atrasada").contains("btn-danger");
        assertThat(linhaDe(corpo, noPrazo)).doesNotContain("overdue-row").doesNotContain("Atrasada");
        assertThat(corpo.indexOf("descartar-" + atrasada)).isLessThan(corpo.indexOf("descartar-" + noPrazo));
    }

    /** O modal traz o trecho para o curador decidir, e a gravidade final começa na percebida (RN-22). */
    @Test
    void modalDeConfirmacaoTrazOTrechoEAGravidadePercebidaMarcada() throws Exception {
        long id = pendente(Instant.now().minus(Duration.ofHours(1)), "MODERADA", "O voo homologado foi em 1906.");

        String modal = modalDe(curadora.get(CAMINHO).body(), "confirmar-" + id);

        assertThat(modal)
                .contains("Confirmar sinalização")
                .contains(PERGUNTA).contains(RESPOSTA).contains("O voo homologado foi em 1906.")
                .contains("action=\"/curadoria/sinalizacoes/" + id + "/confirmar\"")
                .containsPattern("value=\"MODERADA\"[^>]*checked")
                .contains("Erro sobre o conceito central do objetivo BNCC")
                .contains("name=\"_csrf\"");
        assertThat(modalDe(curadora.get(CAMINHO).body(), "descartar-" + id))
                .contains("Descartar sinalização?")
                .contains("action=\"/curadoria/sinalizacoes/" + id + "/descartar\"");
    }

    @Test
    void filaVaziaMostraOAvisoSemTabela() throws Exception {
        String corpo = curadora.get(CAMINHO).body();

        assertThat(corpo).contains("Nenhuma sinalização aguardando triagem").doesNotContain("<table");
    }

    // ---------------------------------------------------------------------------------------------
    // A triagem
    // ---------------------------------------------------------------------------------------------

    @Test
    void confirmarComAGravidadeFinalGravaERegistraNoLog() throws Exception {
        long id = pendente(Instant.now().minus(Duration.ofHours(2)), "GRAVE", "Erro grave.");

        var envio = curadora.postForm(CAMINHO + "/" + id + "/confirmar", "gravidade=MODERADA");

        assertThat(envio.statusCode()).isEqualTo(302);
        assertThat(Navegador.local(envio)).endsWith(CAMINHO);
        assertThat(curadora.get(CAMINHO).body())
                .contains("Sinalização confirmada como Moderada.")
                .doesNotContain("descartar-" + id);
        assertThat(jdbc.queryForMap("SELECT status, gravidade_final, triada_por_id FROM sinalizacao WHERE id = ?", id))
                .containsEntry("status", "CONFIRMADA")
                .containsEntry("gravidade_final", "MODERADA")
                .containsEntry("triada_por_id", curadoraId);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE acao = 'TRIAR_SINALIZACAO' AND entidade_id = ? AND usuario_id = ?",
                        Long.class, String.valueOf(id), curadoraId))
                .isEqualTo(1);
    }

    @Test
    void confirmarSemGravidadeERecusadoENadaMuda() throws Exception {
        long id = pendente(Instant.now().minus(Duration.ofHours(2)), "LEVE", "Sem gravidade.");

        curadora.postForm(CAMINHO + "/" + id + "/confirmar", "");
        curadora.postForm(CAMINHO + "/" + id + "/confirmar", "gravidade=GRAVISSIMA");

        assertThat(curadora.get(CAMINHO).body()).contains(TriagemController.SEM_GRAVIDADE);
        assertThat(jdbc.queryForObject("SELECT status FROM sinalizacao WHERE id = ?", String.class, id))
                .isEqualTo("PENDENTE");
    }

    @Test
    void descartarTiraDaFilaSemGravidade() throws Exception {
        long id = pendente(Instant.now().minus(Duration.ofHours(2)), "LEVE", "Para descartar.");

        var envio = curadora.postForm(CAMINHO + "/" + id + "/descartar", "");

        assertThat(envio.statusCode()).isEqualTo(302);
        assertThat(curadora.get(CAMINHO).body()).contains("Sinalização descartada.").doesNotContain("descartar-" + id);
        assertThat(jdbc.queryForMap("SELECT status, gravidade_final FROM sinalizacao WHERE id = ?", id))
                .containsEntry("status", "DESCARTADA")
                .containsEntry("gravidade_final", null);
    }

    /** Dois curadores na mesma fila: o segundo recebe o aviso, e a primeira decisão vale. */
    @Test
    void segundaTriagemDaMesmaSinalizacaoAvisaENaoMuda() throws Exception {
        long id = pendente(Instant.now().minus(Duration.ofHours(2)), "LEVE", "Duas triagens.");
        curadora.postForm(CAMINHO + "/" + id + "/descartar", "");
        curadora.get(CAMINHO);

        curadora.postForm(CAMINHO + "/" + id + "/confirmar", "gravidade=GRAVE");

        assertThat(curadora.get(CAMINHO).body()).contains(TriagemController.JA_TRIADA);
        assertThat(jdbc.queryForObject("SELECT status FROM sinalizacao WHERE id = ?", String.class, id))
                .isEqualTo("DESCARTADA");
    }

    // ---------------------------------------------------------------------------------------------
    // Abas, perfis e o destaque para o administrador
    // ---------------------------------------------------------------------------------------------

    /** Cada fila é de um módulo: a aba da outra vem pelo HTMX, com o número dela. */
    @Test
    void abasMostramONumeroDaOutraFila() throws Exception {
        pendente(Instant.now().minus(Duration.ofHours(1)), "LEVE", "Uma.");
        pendente(Instant.now().minus(Duration.ofHours(1)), "LEVE", "Outra.");

        assertThat(curadora.get("/curadoria").body())
                .containsPattern("<a href=\"/curadoria\" aria-current=\"page\" class=\"active\">Cenários")
                .contains("hx-get=\"/curadoria/sinalizacoes/contagem\"")
                .contains("href=\"/auditoria/log\"");
        assertThat(curadora.get(CAMINHO + "/contagem").body().strip()).isEqualTo("<span>2</span>");
        assertThat(curadora.get("/curadoria/contagem").body().strip()).matches("<span>\\d+\\+?</span>");
    }

    @Test
    void professorNaoAbreAFilaNemTria() throws Exception {
        long id = pendente(Instant.now().minus(Duration.ofHours(1)), "LEVE", "Do professor.");
        var professor = logado(Perfil.PROFESSOR);

        assertThat(professor.get(CAMINHO).statusCode()).isEqualTo(403);
        assertThat(professor.postForm(CAMINHO + "/" + id + "/descartar", "").statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT status FROM sinalizacao WHERE id = ?", String.class, id))
                .isEqualTo("PENDENTE");
    }

    /** RN-24 e RN-37: o administrador vê quantas passaram do prazo, sem a pergunta, a resposta nem a fila. */
    @Test
    void administradorVeAsAtrasadasSemOsTextos() throws Exception {
        pendente(Instant.now().minus(Duration.ofDays(10)), "GRAVE", "Justificativa que o administrador não vê.");
        pendente(Instant.now().minus(Duration.ofDays(9)), "LEVE", "Outra que o administrador não vê.");
        var administrador = logado(Perfil.ADMINISTRADOR);

        String corpo = administrador.get("/admin/usuarios").body();

        assertThat(corpo)
                .contains("2 sinalizações passaram do prazo de 2 dias úteis para a triagem")
                .doesNotContain("não vê")
                .doesNotContain(RESPOSTA)
                .doesNotContain("href=\"/curadoria/sinalizacoes\"");
        assertThat(administrador.get(CAMINHO).statusCode()).isEqualTo(403);
    }

    @Test
    void semAtrasadasOAdministradorNaoVeOAviso() throws Exception {
        pendente(Instant.now().minus(Duration.ofMinutes(10)), "LEVE", "No prazo.");

        assertThat(logado(Perfil.ADMINISTRADOR).get("/admin/usuarios").body()).doesNotContain("passou do prazo")
                .doesNotContain("passaram do prazo");
    }

    // ---------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------

    private long pendente(Instant criadaEm, String gravidade, String justificativa) {
        long mensagem = jdbc.queryForObject(
                "INSERT INTO mensagem (sessao_id, autoria, texto, criado_em) VALUES (?, 'IA', ?, ?) RETURNING id",
                Long.class, sessaoId, RESPOSTA, criadaEm.minusSeconds(60).atOffset(ZoneOffset.UTC));
        return jdbc.queryForObject(
                """
                INSERT INTO sinalizacao (sessao_id, sessao_iniciada_em, sessao_professor_id, cenario_versao_id,
                                         mensagem_id, pergunta, resposta, respondida_em, tipo, gravidade_percebida,
                                         justificativa, sinalizado_por_id, perfil, criada_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ERRO_HISTORICO', ?, ?, ?, 'PROFESSOR', ?)
                RETURNING id""",
                Long.class, sessaoId, criadaEm.minus(Duration.ofHours(1)).atOffset(ZoneOffset.UTC), professorId,
                versaoId, mensagem, PERGUNTA, RESPOSTA, criadaEm.minusSeconds(60).atOffset(ZoneOffset.UTC), gravidade,
                justificativa, professorId, criadaEm.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
    }

    private Navegador logado(Perfil perfil) throws Exception {
        String email = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Profissional", email, SENHA, Set.of(perfil));
        return Navegador.logado(porta, email, SENHA);
    }

    /** A linha da tabela com os botões desta sinalização. */
    private static String linhaDe(String corpo, long id) {
        int botao = corpo.indexOf("data-abrir-dialogo=\"descartar-" + id + "\"");
        assertThat(botao).as("a linha da sinalização " + id).isNotNegative();
        int inicio = corpo.lastIndexOf("<tr", botao);
        return corpo.substring(inicio, corpo.indexOf("</tr>", botao));
    }

    private static String modalDe(String corpo, String id) {
        var modal = Pattern.compile("<dialog[^>]*id=\"" + id + "\".*?</dialog>", Pattern.DOTALL).matcher(corpo);
        assertThat(modal.find()).as("o modal " + id).isTrue();
        return modal.group();
    }
}
