package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.auditoria.dominio.ForaDoPrazoDeSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.GravidadePercebida;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RespostaNaoEncontrada;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoRepetida;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TipoDeErro;
import br.ufg.inf.labsimulacoes.eventos.SinalizacaoRegistrada;
import br.ufg.inf.labsimulacoes.ia.TextoComDadoPessoal;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quem sinaliza, até quando e o que fica gravado (RF-23, RN-21). O relógio é fixo, para o prazo de 7 dias
 * ser conferido no limite exato. Textos e dados inventados; cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
@RecordApplicationEvents
class RegistroDeSinalizacaoTest {

    private static final Instant INICIO = Instant.parse("2026-11-20T13:00:00Z");
    private static final Instant ENCERRAMENTO = Instant.parse("2026-11-20T13:50:00Z");
    private static final Instant LIMITE = ENCERRAMENTO.plus(Duration.ofDays(7));
    private static final String JUSTIFICATIVA = "O voo homologado foi em 23 de outubro de 1906.";

    @MockitoBean
    private RelogioDaAuditoria relogio;

    @Autowired
    private RegistroDeSinalizacao registro;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry metricas;

    @Autowired
    private ApplicationEvents eventos;

    private DadosDeSessao dados;
    private String professora;
    private long professoraId;
    private long versao;
    private long meta;

    @BeforeEach
    void prepararSessao() {
        dados = new DadosDeSessao(jdbc);
        professora = dados.usuario("PROFESSOR");
        professoraId = dados.idDe(professora);
        versao = dados.cenarioAprovado(professoraId, dados.idDe(dados.usuario("CURADOR"))).versaoId();
        meta = jdbc.queryForObject(
                """
                INSERT INTO resposta_ia_meta (provedor, modelo, parametros, versao_prompt, tokens_entrada, custo)
                VALUES ('openai', 'gpt-6.1-terra', '{"max_tokens": 400}', 'personagem-v1', 316, 0.002)
                RETURNING id""",
                Long.class);
        agora(ENCERRAMENTO.plus(Duration.ofDays(1)));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void donaDaSessaoSinalizaComoProfessoraECopiaOTrecho() {
        long resposta = respostaNaSessao(professoraId, ENCERRAMENTO, meta);
        entrar(professora, "ROLE_PROFESSOR");

        long id = registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.MODERADA, JUSTIFICATIVA);

        Map<String, Object> linha = jdbc.queryForMap("SELECT * FROM sinalizacao WHERE id = ?", id);
        assertThat(linha)
                .containsEntry("mensagem_id", resposta)
                .containsEntry("sessao_professor_id", professoraId)
                .containsEntry("cenario_versao_id", versao)
                .containsEntry("resposta_ia_meta_id", meta)
                .containsEntry("pergunta", "Quando foi o primeiro voo?")
                .containsEntry("resposta", "Voei em 1901, em Paris.")
                .containsEntry("tipo", "ERRO_HISTORICO")
                .containsEntry("gravidade_percebida", "MODERADA")
                .containsEntry("justificativa", JUSTIFICATIVA)
                .containsEntry("sinalizado_por_id", professoraId)
                .containsEntry("perfil", "PROFESSOR")
                .containsEntry("status", "PENDENTE");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT metadados_da_resposta ->> 'provedor' AS provedor,
                               metadados_da_resposta ->> 'modelo' AS modelo,
                               metadados_da_resposta ->> 'versaoPrompt' AS versao,
                               (SELECT count(*) FROM jsonb_object_keys(metadados_da_resposta)) AS chaves,
                               sessao_iniciada_em = ? AS inicio_copiado, criada_em = ? AS no_relogio
                        FROM sinalizacao WHERE id = ?""",
                        INICIO.atOffset(ZoneOffset.UTC), relogio.agora().atOffset(ZoneOffset.UTC), id))
                .containsEntry("provedor", "openai")
                .containsEntry("modelo", "gpt-6.1-terra")
                .containsEntry("versao", "personagem-v1")
                .containsEntry("chaves", 3L)
                .containsEntry("inicio_copiado", true)
                .containsEntry("no_relogio", true);
    }

    /** O evento leva os ids e as escolhas, sem a pergunta, a resposta nem a justificativa. */
    @Test
    void eventoSaiSemOsTextos() {
        long resposta = respostaNaSessao(professoraId, ENCERRAMENTO, meta);
        long sessao = jdbc.queryForObject("SELECT sessao_id FROM mensagem WHERE id = ?", Long.class, resposta);
        entrar(professora, "ROLE_PROFESSOR");

        long id = registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.ANACRONISMO, GravidadePercebida.GRAVE, JUSTIFICATIVA);

        assertThat(eventos.stream(SinalizacaoRegistrada.class)).containsExactly(new SinalizacaoRegistrada(
                id, sessao, versao, "ANACRONISMO", "GRAVE", "PROFESSOR", professoraId, relogio.agora()));
        assertThat(eventos.stream(SinalizacaoRegistrada.class).findFirst().orElseThrow().toString())
                .doesNotContain("Quando foi", "Voei em", JUSTIFICATIVA);
    }

    @Test
    void professoraSinalizaDuranteASessao() {
        long resposta = respostaNaSessao(professoraId, null, meta);
        agora(INICIO.plus(Duration.ofMinutes(10)));
        entrar(professora, "ROLE_PROFESSOR");

        registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.ERRO_CIENTIFICO, GravidadePercebida.LEVE, JUSTIFICATIVA);

        assertThat(perfilDe(resposta)).isEqualTo("PROFESSOR");
    }

    @Test
    void professoraSinalizaNoLimiteExatoDosSeteDias() {
        long resposta = respostaNaSessao(professoraId, ENCERRAMENTO, meta);
        agora(LIMITE);
        entrar(professora, "ROLE_PROFESSOR");

        registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA);

        assertThat(perfilDe(resposta)).isEqualTo("PROFESSOR");
    }

    @Test
    void professoraNaoSinalizaDepoisDosSeteDias() {
        long resposta = respostaNaSessao(professoraId, ENCERRAMENTO, meta);
        agora(LIMITE.plusNanos(1_000));
        entrar(professora, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> registro.sinalizar(
                        sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA))
                .isInstanceOfSatisfying(ForaDoPrazoDeSinalizacao.class,
                        erro -> assertThat(erro.limite()).isEqualTo(LIMITE));
        assertNadaGravado();
    }

    /** Uma sessão que nunca foi encerrada conta do início (RN-21). */
    @Test
    void sessaoAbertaHaMaisDeSeteDiasNaoAceitaAProfessora() {
        long resposta = respostaNaSessao(professoraId, null, meta);
        agora(INICIO.plus(Duration.ofDays(7)).plusSeconds(1));
        entrar(professora, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> registro.sinalizar(
                        sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA))
                .isInstanceOf(ForaDoPrazoDeSinalizacao.class);
    }

    /** A sessão de outro professor não existe para quem só é professor, nem dentro do prazo (RN-37). */
    @Test
    void outroProfessorNaoSinaliza() {
        long resposta = respostaNaSessao(professoraId, ENCERRAMENTO, meta);
        entrar(dados.usuario("PROFESSOR"), "ROLE_PROFESSOR");

        assertThatThrownBy(() -> registro.sinalizar(
                        sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA))
                .isInstanceOf(RespostaNaoEncontrada.class);
        assertNadaGravado();
    }

    @Test
    void curadorSinalizaQualquerSessaoAQualquerMomento() {
        long resposta = respostaNaSessao(professoraId, ENCERRAMENTO, meta);
        agora(LIMITE.plus(Duration.ofDays(30)));
        String curador = dados.usuario("CURADOR");
        entrar(curador, "ROLE_CURADOR");

        long id = registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.SAIDA_DO_PERSONAGEM, GravidadePercebida.GRAVE, JUSTIFICATIVA);

        assertThat(jdbc.queryForMap("SELECT perfil, sinalizado_por_id FROM sinalizacao WHERE id = ?", id))
                .containsEntry("perfil", "CURADOR")
                .containsEntry("sinalizado_por_id", dados.idDe(curador));
    }

    /** Quem tem os dois perfis é professor na própria sessão, no prazo, e curador nos outros casos. */
    @Test
    void doisPerfisSinalizaComoProfessorSoNaPropriaSessaoNoPrazo() {
        String dupla = dados.usuario("PROFESSOR", "CURADOR");
        long dona = dados.idDe(dupla);
        long propriaNoPrazo = respostaNaSessao(dona, ENCERRAMENTO, meta);
        long propriaForaDoPrazo = respostaNaSessao(dona, INICIO.minus(Duration.ofDays(20)), null);
        long deOutra = respostaNaSessao(professoraId, ENCERRAMENTO, null);
        entrar(dupla, "ROLE_PROFESSOR", "ROLE_CURADOR");

        registro.sinalizar(sessaoDe(propriaNoPrazo), propriaNoPrazo, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA);
        registro.sinalizar(sessaoDe(propriaForaDoPrazo), propriaForaDoPrazo, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA);
        registro.sinalizar(sessaoDe(deOutra), deOutra, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA);

        assertThat(perfilDe(propriaNoPrazo)).isEqualTo("PROFESSOR");
        assertThat(perfilDe(propriaForaDoPrazo)).isEqualTo("CURADOR");
        assertThat(perfilDe(deOutra)).isEqualTo("CURADOR");
    }

    @Test
    void mesmaPessoaNaoSinalizaDuasVezesMasOutraPessoaSim() {
        long resposta = respostaNaSessao(professoraId, ENCERRAMENTO, meta);
        entrar(professora, "ROLE_PROFESSOR");
        registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA);

        assertThatThrownBy(() -> registro.sinalizar(
                        sessaoDe(resposta), resposta, TipoDeErro.ANACRONISMO, GravidadePercebida.GRAVE, "Outra justificativa"))
                .isInstanceOf(SinalizacaoRepetida.class);

        entrar(dados.usuario("CURADOR"), "ROLE_CURADOR");
        registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sinalizacao WHERE mensagem_id = ?", Long.class, resposta))
                .isEqualTo(2);
        assertThat(eventos.stream(SinalizacaoRegistrada.class)).hasSize(2);
    }

    /** RNF-01: a justificativa fica gravada para sempre, então não pode ter dado pessoal. */
    @Test
    void justificativaComDadoPessoalNaoEGravada() {
        long resposta = respostaNaSessao(professoraId, ENCERRAMENTO, meta);
        entrar(professora, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE,
                        "Liguem para a mãe dele no 98765-4321"))
                .isInstanceOfSatisfying(TextoComDadoPessoal.class,
                        erro -> assertThat(erro.aviso("A justificativa")).startsWith("A justificativa parece ter"));
        assertNadaGravado();
    }

    /** A pergunta e a resposta copiadas não passam pelo bloqueio: a sinalização denuncia o que veio. */
    @Test
    void respostaComAlgoParecidoComDadoPessoalECopiadaComoEsta() {
        long sessao = sessao(professoraId, ENCERRAMENTO);
        mensagem(sessao, "TURMA", "Como falo com o senhor?", 1, null);
        long resposta = mensagem(sessao, "IA", "Escreva para santos@exemplo.com.", 2, meta);
        entrar(professora, "ROLE_PROFESSOR");

        long id = registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.SAIDA_DO_PERSONAGEM, GravidadePercebida.GRAVE, JUSTIFICATIVA);

        assertThat(jdbc.queryForObject("SELECT resposta FROM sinalizacao WHERE id = ?", String.class, id))
                .isEqualTo("Escreva para santos@exemplo.com.");
    }

    /** Uma resposta antiga gravada sem metadados também pode ser sinalizada. */
    @Test
    void respostaSemMetadadosFicaSemACopia() {
        long resposta = respostaNaSessao(professoraId, ENCERRAMENTO, null);
        entrar(professora, "ROLE_PROFESSOR");

        long id = registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA);

        assertThat(jdbc.queryForMap(
                        "SELECT resposta_ia_meta_id, metadados_da_resposta FROM sinalizacao WHERE id = ?", id))
                .containsEntry("resposta_ia_meta_id", null)
                .containsEntry("metadados_da_resposta", null);
    }

    @Test
    void perguntaDaTurmaEMensagemQueNaoExisteNaoSaoSinalizadas() {
        long sessao = sessao(professoraId, ENCERRAMENTO);
        long pergunta = mensagem(sessao, "TURMA", "Quando foi o primeiro voo?", 1, null);
        entrar(professora, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> registro.sinalizar(
                        sessaoDe(pergunta), pergunta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA))
                .isInstanceOf(RespostaNaoEncontrada.class);
        assertThatThrownBy(() -> registro.sinalizar(
                        1, Long.MAX_VALUE, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA))
                .isInstanceOf(RespostaNaoEncontrada.class);
    }

    @Test
    void pedidoIncompletoNaoEGravado() {
        long resposta = respostaNaSessao(professoraId, ENCERRAMENTO, meta);
        entrar(professora, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> registro.sinalizar(sessaoDe(resposta), resposta, null, GravidadePercebida.LEVE, JUSTIFICATIVA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, null, JUSTIFICATIVA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registro.sinalizar(
                        sessaoDe(resposta), resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, "a".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
        assertNadaGravado();
    }

    @Test
    void metricaContaPorTipoEPerfil() {
        long resposta = respostaNaSessao(professoraId, ENCERRAMENTO, meta);
        double antes = sinalizacoes("anacronismo", "professor");
        entrar(professora, "ROLE_PROFESSOR");

        registro.sinalizar(sessaoDe(resposta), resposta, TipoDeErro.ANACRONISMO, GravidadePercebida.LEVE, JUSTIFICATIVA);

        assertThat(sinalizacoes("anacronismo", "professor")).isEqualTo(antes + 1);
        assertThat(metricas.find(RegistroDeSinalizacao.METRICA).counters())
                .allSatisfy(contador -> assertThat(contador.getId().getTags())
                        .extracting(tag -> tag.getKey())
                        .containsExactlyInAnyOrder("tipo", "perfil"));
    }

    @Test
    void administradorNaoSinaliza() {
        entrar(dados.usuario("ADMINISTRADOR"), "ROLE_ADMINISTRADOR");

        assertThatThrownBy(() -> registro.sinalizar(1, 1, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.LEVE, JUSTIFICATIVA))
                .isInstanceOf(AccessDeniedException.class);
    }

    private long respostaNaSessao(long professorId, Instant encerradaEm, Long metaId) {
        long sessao = sessao(professorId, encerradaEm);
        mensagem(sessao, "TURMA", "Quando foi o primeiro voo?", 1, null);
        return mensagem(sessao, "IA", "Voei em 1901, em Paris.", 2, metaId);
    }

    private long sessao(long professorId, Instant encerradaEm) {
        Instant inicio = encerradaEm == null || encerradaEm.equals(ENCERRAMENTO)
                ? INICIO
                : encerradaEm.minus(Duration.ofMinutes(50));
        return jdbc.queryForObject(
                """
                INSERT INTO sessao (professor_id, turma_id, cenario_versao_id, limite_perguntas, status, iniciada_em,
                                    encerrada_em)
                VALUES (?, ?, ?, 40, ?, ?, ?) RETURNING id""",
                Long.class,
                professorId,
                dados.turma(),
                versao,
                encerradaEm == null ? "EM_ANDAMENTO" : "ENCERRADA",
                inicio.atOffset(ZoneOffset.UTC),
                encerradaEm == null ? null : encerradaEm.atOffset(ZoneOffset.UTC));
    }

    private long mensagem(long sessao, String autoria, String texto, int segundos, Long metaId) {
        return jdbc.queryForObject(
                """
                INSERT INTO mensagem (sessao_id, autoria, texto, resposta_ia_meta_id, criado_em)
                VALUES (?, ?, ?, ?, ?) RETURNING id""",
                Long.class, sessao, autoria, texto, metaId, INICIO.plusSeconds(segundos).atOffset(ZoneOffset.UTC));
    }

    private long sessaoDe(long mensagem) {
        return jdbc.queryForObject("SELECT sessao_id FROM mensagem WHERE id = ?", Long.class, mensagem);
    }

    private String perfilDe(long resposta) {
        return jdbc.queryForObject("SELECT perfil FROM sinalizacao WHERE mensagem_id = ?", String.class, resposta);
    }

    private void assertNadaGravado() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sinalizacao WHERE cenario_versao_id = ?", Long.class, versao))
                .isZero();
        assertThat(eventos.stream(SinalizacaoRegistrada.class)).isEmpty();
    }

    private double sinalizacoes(String tipo, String perfil) {
        Counter contador = metricas.find(RegistroDeSinalizacao.METRICA).tag("tipo", tipo).tag("perfil", perfil).counter();
        return contador == null ? 0 : contador.count();
    }

    private void agora(Instant momento) {
        when(relogio.agora()).thenReturn(momento);
    }

    private static void entrar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }
}
