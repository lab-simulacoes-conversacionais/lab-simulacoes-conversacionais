package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.auditoria.dominio.GravidadePercebida;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TipoDeErro;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Critério de aceite da RF-23: a sinalização continua completa depois da exclusão das conversas (RN-33,
 * Stack, seção 6).
 *
 * <p>O job de retenção ainda não existe (RNF-15), então o teste faz o que ele faria, no pior caso: apaga
 * as mensagens, os eventos da sessão, a própria sessão e os metadados da resposta. A sessão do teste não
 * tem caso bloqueado, cuja referência à sessão impediria apagá-la. Textos inventados; o teste desfaz o que
 * gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class SinalizacaoAposARetencaoTest {

    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Instant INICIO = AGORA.minus(Duration.ofHours(2));
    private static final Instant ENCERRAMENTO = INICIO.plus(Duration.ofMinutes(45));
    private static final String PERGUNTA = "Quando foi o primeiro voo?";
    private static final String RESPOSTA = "Voei em 1901, em Paris.";
    private static final String JUSTIFICATIVA = "O voo homologado foi em 23 de outubro de 1906.";

    @Autowired
    private RegistroDeSinalizacao registro;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private String professora;
    private long professoraId;
    private long versao;
    private long sessao;
    private long meta;
    private long resposta;

    @BeforeEach
    void prepararSessaoEncerrada() {
        dados = new DadosDeSessao(jdbc);
        professora = dados.usuario("PROFESSOR");
        professoraId = dados.idDe(professora);
        versao = dados.cenarioAprovado(professoraId, dados.idDe(dados.usuario("CURADOR"))).versaoId();
        sessao = jdbc.queryForObject(
                """
                INSERT INTO sessao (professor_id, turma_id, cenario_versao_id, limite_perguntas, status, iniciada_em,
                                    encerrada_em)
                VALUES (?, ?, ?, 40, 'ENCERRADA', ?, ?) RETURNING id""",
                Long.class, professoraId, dados.turma(), versao, utc(INICIO), utc(ENCERRAMENTO));
        jdbc.update("INSERT INTO evento_sessao (sessao_id, tipo, motivo, ocorrido_em) VALUES (?, 'PAUSA', 'MANUAL', ?)",
                sessao, utc(INICIO.plusSeconds(60)));
        jdbc.update("INSERT INTO evento_sessao (sessao_id, tipo, ocorrido_em) VALUES (?, 'RETOMADA', ?)",
                sessao, utc(INICIO.plusSeconds(120)));
        meta = jdbc.queryForObject(
                """
                INSERT INTO resposta_ia_meta (provedor, modelo, parametros, versao_prompt, tokens_entrada, custo)
                VALUES ('openai', 'gpt-6.1-terra', '{"max_tokens": 400}', 'personagem-v1', 316, 0.002)
                RETURNING id""",
                Long.class);
        dados.mensagem(sessao, "TURMA", PERGUNTA, INICIO.plusSeconds(180));
        resposta = jdbc.queryForObject(
                """
                INSERT INTO mensagem (sessao_id, autoria, texto, resposta_ia_meta_id, criado_em)
                VALUES (?, 'IA', ?, ?, ?) RETURNING id""",
                Long.class, sessao, RESPOSTA, meta, utc(INICIO.plusSeconds(190)));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinalizacoesContinuamCompletasDepoisDaExclusaoDasConversas() {
        entrar(professora, "ROLE_PROFESSOR");
        long daProfessora = registro.sinalizar(resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.GRAVE, JUSTIFICATIVA);
        String curador = dados.usuario("CURADOR");
        entrar(curador, "ROLE_CURADOR");
        long doCurador = registro.sinalizar(resposta, TipoDeErro.ANACRONISMO, GravidadePercebida.MODERADA, JUSTIFICATIVA);

        apagarComoARetencao();

        assertThat(contar("SELECT count(*) FROM mensagem WHERE sessao_id = ?", sessao)).isZero();
        assertThat(contar("SELECT count(*) FROM sessao WHERE id = ?", sessao)).isZero();
        assertThat(contar("SELECT count(*) FROM resposta_ia_meta WHERE id = ?", meta)).isZero();
        assertCompleta(daProfessora, professoraId, "PROFESSOR", "ERRO_HISTORICO", "GRAVE");
        assertCompleta(doCurador, dados.idDe(curador), "CURADOR", "ANACRONISMO", "MODERADA");
    }

    /** Só as referências ficam nulas; tudo o que a triagem usa continua na própria sinalização. */
    private void assertCompleta(long id, long quem, String perfil, String tipo, String gravidade) {
        Map<String, Object> linha = jdbc.queryForMap(
                """
                SELECT sessao_id, mensagem_id, resposta_ia_meta_id, sessao_professor_id, cenario_versao_id, pergunta,
                       resposta, tipo, gravidade_percebida, justificativa, sinalizado_por_id, perfil, status,
                       metadados_da_resposta ->> 'provedor' AS provedor,
                       metadados_da_resposta ->> 'modelo' AS modelo,
                       metadados_da_resposta ->> 'versaoPrompt' AS versao_prompt,
                       sessao_iniciada_em = ? AS inicio_copiado,
                       respondida_em = ? AS resposta_no_momento_certo
                FROM sinalizacao WHERE id = ?""",
                utc(INICIO), utc(INICIO.plusSeconds(190)), id);
        assertThat(linha)
                .containsEntry("sessao_id", null)
                .containsEntry("mensagem_id", null)
                .containsEntry("resposta_ia_meta_id", null)
                .containsEntry("sessao_professor_id", professoraId)
                .containsEntry("cenario_versao_id", versao)
                .containsEntry("pergunta", PERGUNTA)
                .containsEntry("resposta", RESPOSTA)
                .containsEntry("tipo", tipo)
                .containsEntry("gravidade_percebida", gravidade)
                .containsEntry("justificativa", JUSTIFICATIVA)
                .containsEntry("sinalizado_por_id", quem)
                .containsEntry("perfil", perfil)
                .containsEntry("status", "PENDENTE")
                .containsEntry("provedor", "openai")
                .containsEntry("modelo", "gpt-6.1-terra")
                .containsEntry("versao_prompt", "personagem-v1")
                .containsEntry("inicio_copiado", true)
                .containsEntry("resposta_no_momento_certo", true);
    }

    /** O pior caso da retenção: a conversa, os eventos, a sessão e os metadados. */
    private void apagarComoARetencao() {
        jdbc.update("DELETE FROM mensagem WHERE sessao_id = ?", sessao);
        jdbc.update("DELETE FROM evento_sessao WHERE sessao_id = ?", sessao);
        jdbc.update("DELETE FROM sessao WHERE id = ?", sessao);
        jdbc.update("DELETE FROM resposta_ia_meta WHERE id = ?", meta);
    }

    private long contar(String sql, long id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }

    private static java.time.OffsetDateTime utc(Instant momento) {
        return momento.atOffset(ZoneOffset.UTC);
    }

    private static void entrar(String email, String perfil) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfil));
    }
}
