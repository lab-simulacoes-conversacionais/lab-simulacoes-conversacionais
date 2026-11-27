package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
 * Critérios de aceite da issue 44 na consulta do log, pela tela e com requisições HTTP reais (RF-26, RN-35):
 * o curador e o administrador consultam, com os filtros de período, usuário e ação; o professor não. O log
 * não pode ser apagado, e outros testes gravam nele, então cada teste filtra pela pessoa que ele mesmo
 * criou. Textos inventados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InfraestruturaDeTeste.class)
class LogDeAuditoriaPelaTelaTest {

    private static final String SENHA = "uma senha de teste";
    private static final String HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();

    @LocalServerPort
    private int porta;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private JdbcTemplate jdbc;

    private Navegador curadora;
    private long curadoraId;
    private long sinalizacao;

    /** A curadora descarta uma sinalização pela fila, e a triagem vai para o log em nome dela. */
    @BeforeEach
    void triarUmaSinalizacao() throws Exception {
        String email = UUID.randomUUID() + "@escola.test";
        curadoraId = cadastro.cadastrar("Curadora do Log", email, SENHA, Set.of(Perfil.CURADOR)).id();
        curadora = Navegador.logado(porta, email, SENHA);
        sinalizacao = pendente();
        assertThat(curadora.postForm("/curadoria/sinalizacoes/" + sinalizacao + "/descartar", "").statusCode())
                .isEqualTo(302);
    }

    /** Como no protótipo: a ação, quem fez e quando, só para leitura. */
    @Test
    void curadorConsultaOLogComOQueFoiFeitoEQuemFez() throws Exception {
        var pagina = curadora.get("/auditoria/log?usuario=" + curadoraId);

        assertThat(pagina.statusCode()).isEqualTo(200);
        assertThat(pagina.body())
                .contains("Log de auditoria")
                .contains("Somente leitura")
                .contains("Voltar à fila do curador")
                .doesNotContain("Seções da administração")
                .contains("Sinalização descartada")
                .contains("Sinalização nº " + sinalizacao)
                .contains("Curadora do Log")
                .containsPattern("<option value=\"" + curadoraId + "\" selected=\"selected\">Curadora do Log</option>")
                .doesNotContain("<form method=\"post\"");
    }

    @Test
    void administradorVeOLogComoAbaDaAdministracao() throws Exception {
        var administrador = logado(Perfil.ADMINISTRADOR);

        String corpo = administrador.get("/auditoria/log?usuario=" + curadoraId).body();

        assertThat(corpo)
                .contains("Seções da administração")
                .containsPattern("<a href=\"/auditoria/log\" aria-current=\"page\" class=\"active\">Log de auditoria</a>")
                .contains("Sinalização descartada")
                .contains("Curadora do Log");
        assertThat(administrador.get("/admin/usuarios").body()).contains("href=\"/auditoria/log\"");
    }

    @Test
    void filtrosDeAcaoEPeriodoSeCombinam() throws Exception {
        String base = "/auditoria/log?usuario=" + curadoraId;

        assertThat(curadora.get(base + "&acao=TRIAR_SINALIZACAO&de=" + HOJE + "&ate=" + HOJE).body())
                .contains("Sinalização nº " + sinalizacao)
                .containsPattern("value=\"TRIAR_SINALIZACAO\" selected")
                .contains("value=\"" + HOJE + "\"")
                .contains("Limpar");
        assertThat(curadora.get(base + "&acao=APROVAR_CENARIO").body())
                .contains("Nenhuma ação encontrada com estes filtros")
                .doesNotContain("Sinalização nº " + sinalizacao);
        String ontem = LocalDate.parse(HOJE).minusDays(1).toString();
        assertThat(curadora.get(base + "&ate=" + ontem).body()).doesNotContain("Sinalização nº " + sinalizacao);
    }

    @Test
    void periodoInvertidoOuDataInvalidaAvisam() throws Exception {
        String amanha = LocalDate.parse(HOJE).plusDays(1).toString();

        assertThat(curadora.get("/auditoria/log?de=" + amanha + "&ate=" + HOJE).body())
                .contains(LogDeAuditoriaController.PERIODO_INVERTIDO)
                .doesNotContain("<table");
        assertThat(curadora.get("/auditoria/log?de=ontem").body()).contains(LogDeAuditoriaController.DATA_INVALIDA);
        // Um valor fora da lista vale como "todas as ações", e um cursor inválido volta à primeira página.
        assertThat(curadora.get("/auditoria/log?usuario=" + curadoraId + "&acao=APAGAR&antesDe=x&antesDeId=1").body())
                .contains("Sinalização nº " + sinalizacao);
    }

    @Test
    void professorNaoConsultaOLog() throws Exception {
        assertThat(logado(Perfil.PROFESSOR).get("/auditoria/log").statusCode()).isEqualTo(403);
    }

    // ---------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------

    private long pendente() {
        var dados = new DadosDeSessao(jdbc);
        long professor = dados.idDe(dados.usuario("PROFESSOR"));
        long versao = dados.cenarioAprovado(professor, dados.idDe(dados.usuario("CURADOR"))).versaoId();
        Instant inicio = Instant.now().minus(Duration.ofHours(3));
        long sessao = jdbc.queryForObject(
                """
                INSERT INTO sessao (professor_id, turma_id, cenario_versao_id, limite_perguntas, status, iniciada_em,
                                    encerrada_em)
                VALUES (?, ?, ?, 40, 'ENCERRADA', ?, ?) RETURNING id""",
                Long.class, professor, dados.turma(), versao, inicio.atOffset(ZoneOffset.UTC),
                inicio.plusSeconds(3000).atOffset(ZoneOffset.UTC));
        long mensagem = jdbc.queryForObject(
                "INSERT INTO mensagem (sessao_id, autoria, texto, criado_em) VALUES (?, 'IA', 'Voei em 1901.', ?) RETURNING id",
                Long.class, sessao, inicio.plusSeconds(60).atOffset(ZoneOffset.UTC));
        return jdbc.queryForObject(
                """
                INSERT INTO sinalizacao (sessao_id, sessao_iniciada_em, sessao_professor_id, cenario_versao_id,
                                         mensagem_id, pergunta, resposta, respondida_em, tipo, gravidade_percebida,
                                         justificativa, sinalizado_por_id, perfil, criada_em)
                VALUES (?, ?, ?, ?, ?, 'Quando foi?', 'Voei em 1901.', ?, 'ERRO_HISTORICO', 'LEVE', 'Data errada.', ?,
                        'PROFESSOR', ?)
                RETURNING id""",
                Long.class, sessao, inicio.atOffset(ZoneOffset.UTC), professor, versao, mensagem,
                inicio.plusSeconds(60).atOffset(ZoneOffset.UTC), professor,
                Instant.now().minus(Duration.ofHours(1)).atOffset(ZoneOffset.UTC));
    }

    private Navegador logado(Perfil perfil) throws Exception {
        String email = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Profissional", email, SENHA, Set.of(perfil));
        return Navegador.logado(porta, email, SENHA);
    }
}
