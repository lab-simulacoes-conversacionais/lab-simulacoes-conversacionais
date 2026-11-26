package br.ufg.inf.labsimulacoes.auditoria.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * A tabela de sinalizações no PostgreSQL real (RF-23, RN-21): restrições, valores padrão e as referências
 * que ficam nulas quando a retenção apaga a mensagem, os metadados e a sessão (RN-33). Os textos são
 * inventados. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class SinalizacoesJdbcTest {

    private static final Instant INICIO = Instant.parse("2026-11-20T13:00:00Z");
    private static final Instant RESPOSTA = Instant.parse("2026-11-20T13:05:00Z");
    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final String METADADOS = "{\"provedor\": \"openai\", \"modelo\": \"m\", \"versaoPrompt\": \"v1\"}";

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private long professor;
    private long curador;
    private long versao;
    private long sessao;
    private long meta;
    private long mensagem;

    @BeforeEach
    void prepararResposta() {
        dados = new DadosDeSessao(jdbc);
        professor = dados.idDe(dados.usuario("PROFESSOR"));
        curador = dados.idDe(dados.usuario("CURADOR"));
        versao = dados.cenarioAprovado(professor, curador).versaoId();
        sessao = jdbc.queryForObject(
                """
                INSERT INTO sessao (professor_id, turma_id, cenario_versao_id, limite_perguntas, iniciada_em)
                VALUES (?, ?, ?, 40, ?) RETURNING id""",
                Long.class, professor, dados.turma(), versao, INICIO.atOffset(ZoneOffset.UTC));
        meta = jdbc.queryForObject(
                "INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt) VALUES ('openai', 'm', 'v1') RETURNING id",
                Long.class);
        dados.mensagem(sessao, "TURMA", "Como foi o primeiro voo?", RESPOSTA.minusSeconds(10));
        mensagem = jdbc.queryForObject(
                """
                INSERT INTO mensagem (sessao_id, autoria, texto, resposta_ia_meta_id, criado_em)
                VALUES (?, 'IA', 'Voei em 1906, em Paris.', ?, ?) RETURNING id""",
                Long.class, sessao, meta, RESPOSTA.atOffset(ZoneOffset.UTC));
    }

    @Test
    void sinalizacaoCompletaEntraPendente() {
        long id = sinalizar(professor, "PROFESSOR");

        assertThat(linha(id))
                .containsEntry("sessao_id", sessao)
                .containsEntry("sessao_professor_id", professor)
                .containsEntry("cenario_versao_id", versao)
                .containsEntry("mensagem_id", mensagem)
                .containsEntry("resposta_ia_meta_id", meta)
                .containsEntry("pergunta", "Como foi o primeiro voo?")
                .containsEntry("resposta", "Voei em 1906, em Paris.")
                .containsEntry("tipo", "ERRO_HISTORICO")
                .containsEntry("gravidade_percebida", "MODERADA")
                .containsEntry("perfil", "PROFESSOR")
                .containsEntry("status", "PENDENTE");
        assertThat(jdbc.queryForObject(
                        "SELECT metadados_da_resposta ->> 'modelo' FROM sinalizacao WHERE id = ?", String.class, id))
                .isEqualTo("m");
    }

    @ParameterizedTest(name = "[{index}] {0} = {1}")
    @CsvSource({
        "tipo, ERRO_DE_DIGITACAO",
        "gravidade_percebida, GRAVISSIMA",
        "perfil, ADMINISTRADOR",
        "justificativa, '   '"
    })
    void valorForaDaRegraERecusado(String coluna, String valor) {
        long id = sinalizar(professor, "PROFESSOR");

        assertThatThrownBy(() -> jdbc.update("UPDATE sinalizacao SET " + coluna + " = ? WHERE id = ?", valor, id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void metadadosPrecisamSerUmObjeto() {
        long id = sinalizar(professor, "PROFESSOR");

        assertThatThrownBy(() ->
                        jdbc.update("UPDATE sinalizacao SET metadados_da_resposta = '[]'::jsonb WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Uma resposta antiga gravada sem metadados ainda pode ser sinalizada. */
    @Test
    void respostaSemMetadadosPodeSerSinalizada() {
        long id = sinalizar(professor, "PROFESSOR", null, null);

        assertThat(linha(id)).containsEntry("resposta_ia_meta_id", null).containsEntry("metadados_da_resposta", null);
    }

    @Test
    void mesmaPessoaNaoSinalizaAMesmaMensagemDuasVezes() {
        sinalizar(professor, "PROFESSOR");

        assertThatThrownBy(() -> sinalizar(professor, "PROFESSOR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outraPessoaSinalizaAMesmaMensagem() {
        sinalizar(professor, "PROFESSOR");
        sinalizar(curador, "CURADOR");

        assertThat(contarDaMensagem()).isEqualTo(2);
    }

    /**
     * A retenção apaga as mensagens, os metadados e a sessão: as referências ficam nulas, nada é apagado
     * em cascata, as cópias continuam e as sinalizações que ficaram sem mensagem não colidem entre si.
     */
    @Test
    void retencaoDeixaAsReferenciasNulasEASinalizacaoCompleta() {
        long doProfessor = sinalizar(professor, "PROFESSOR");
        long doCurador = sinalizar(curador, "CURADOR");

        jdbc.update("DELETE FROM mensagem WHERE sessao_id = ?", sessao);
        jdbc.update("DELETE FROM resposta_ia_meta WHERE id = ?", meta);
        jdbc.update("DELETE FROM sessao WHERE id = ?", sessao);

        for (long id : new long[] {doProfessor, doCurador}) {
            Map<String, Object> linha = linha(id);
            assertThat(linha)
                    .containsEntry("sessao_id", null)
                    .containsEntry("mensagem_id", null)
                    .containsEntry("resposta_ia_meta_id", null)
                    .containsEntry("sessao_professor_id", professor)
                    .containsEntry("cenario_versao_id", versao)
                    .containsEntry("pergunta", "Como foi o primeiro voo?")
                    .containsEntry("resposta", "Voei em 1906, em Paris.");
            assertThat(linha.get("metadados_da_resposta")).isNotNull();
        }
    }

    private long sinalizar(long quem, String perfil) {
        return sinalizar(quem, perfil, meta, METADADOS);
    }

    private long sinalizar(long quem, String perfil, Long metaId, String metadados) {
        return jdbc.queryForObject(
                """
                INSERT INTO sinalizacao (sessao_id, sessao_iniciada_em, sessao_professor_id, cenario_versao_id,
                                         mensagem_id, resposta_ia_meta_id, metadados_da_resposta, pergunta, resposta,
                                         respondida_em, tipo, gravidade_percebida, justificativa, sinalizado_por_id,
                                         perfil, criada_em)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'Como foi o primeiro voo?', 'Voei em 1906, em Paris.', ?,
                        'ERRO_HISTORICO', 'MODERADA', 'O primeiro voo homologado foi em outubro de 1906.', ?, ?, ?)
                RETURNING id""",
                Long.class,
                sessao, INICIO.atOffset(ZoneOffset.UTC), professor, versao, mensagem, metaId, metadados,
                RESPOSTA.atOffset(ZoneOffset.UTC), quem, perfil, AGORA.atOffset(ZoneOffset.UTC));
    }

    private Map<String, Object> linha(long id) {
        return jdbc.queryForMap("SELECT * FROM sinalizacao WHERE id = ?", id);
    }

    private int contarDaMensagem() {
        return jdbc.queryForObject("SELECT count(*) FROM sinalizacao WHERE mensagem_id = ?", Integer.class, mensagem);
    }
}
