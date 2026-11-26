package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.MensagemDaRevisao;
import br.ufg.inf.labsimulacoes.sessoes.SessaoParaRevisao;
import br.ufg.inf.labsimulacoes.sessoes.TrechoParaSinalizar;
import br.ufg.inf.labsimulacoes.sessoes.TrechosDasSessoes;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * A conversa gravada para a sinalização e a revisão (RF-23): cada resposta com a pergunta que a originou,
 * inclusive depois de um reinício, e as sessões recentes do professor. Textos inventados; cada teste
 * desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class TrechosDasSessoesTest {

    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Instant INICIO = AGORA.minus(Duration.ofHours(2));

    @Autowired
    private TrechosDasSessoes trechos;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private long professor;
    private long versao;
    private long turma;

    @BeforeEach
    void prepararCenario() {
        dados = new DadosDeSessao(jdbc);
        professor = dados.idDe(dados.usuario("PROFESSOR"));
        versao = dados.cenarioAprovado(professor, dados.idDe(dados.usuario("CURADOR"))).versaoId();
        turma = dados.turma();
        entrar("ROLE_PROFESSOR");
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void respostaVemComAPerguntaQueAOriginouEOsDadosDaSessao() {
        long sessao = sessao(professor, INICIO, null, "SIMULACAO");
        long meta = jdbc.queryForObject(
                "INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt) VALUES ('openai', 'm', 'v1') RETURNING id",
                Long.class);
        mensagem(sessao, "TURMA", "Quando foi o voo?", 1);
        mensagem(sessao, "IA", "Em 1906.", 2);
        mensagem(sessao, "TURMA", "Onde foi o voo?", 3);
        long resposta = mensagem(sessao, "IA", "Em Paris, no campo de Bagatelle.", 4, meta);

        TrechoParaSinalizar trecho = trechos.resposta(resposta).orElseThrow();

        assertThat(trecho.pergunta()).isEqualTo("Onde foi o voo?");
        assertThat(trecho.resposta()).isEqualTo("Em Paris, no campo de Bagatelle.");
        assertThat(trecho.respondidaEm()).isEqualTo(INICIO.plusSeconds(4));
        assertThat(trecho.respostaIaMetaId()).isEqualTo(meta);
        assertThat(trecho)
                .extracting(TrechoParaSinalizar::sessaoId, TrechoParaSinalizar::professorId,
                        TrechoParaSinalizar::versaoId, TrechoParaSinalizar::sessaoIniciadaEm,
                        TrechoParaSinalizar::sessaoEncerradaEm, TrechoParaSinalizar::mensagemId)
                .containsExactly(sessao, professor, versao, INICIO, null, resposta);
    }

    /**
     * Uma pergunta com a resposta bloqueada não tem resposta, e o reinício não apaga nada: a resposta
     * continua ligada à pergunta logo antes dela.
     */
    @Test
    void respostaDepoisDeUmBloqueioEDeUmReinicioContinuaComASuaPergunta() {
        long sessao = sessao(professor, INICIO, null, "SIMULACAO");
        mensagem(sessao, "TURMA", "Pergunta sem resposta", 1);
        jdbc.update("UPDATE mensagem SET resposta_bloqueada = true WHERE sessao_id = ?", sessao);
        jdbc.update("INSERT INTO evento_sessao (sessao_id, tipo, ocorrido_em) VALUES (?, 'REINICIO', ?)",
                sessao, INICIO.plusSeconds(2).atOffset(ZoneOffset.UTC));
        mensagem(sessao, "TURMA", "Como era o 14-bis?", 3);
        long resposta = mensagem(sessao, "IA", "Era feito de bambu e seda.", 4);

        assertThat(trechos.resposta(resposta).orElseThrow().pergunta()).isEqualTo("Como era o 14-bis?");
        assertThat(trechos.resposta(resposta).orElseThrow().respostaIaMetaId()).isNull();
    }

    @Test
    void perguntaDaTurmaEMensagemQueNaoExisteNaoSaoRespostas() {
        long sessao = sessao(professor, INICIO, null, "SIMULACAO");
        long pergunta = mensagem(sessao, "TURMA", "Quando foi o voo?", 1);

        assertThat(trechos.resposta(pergunta)).isEmpty();
        assertThat(trechos.resposta(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void conversaVemInteiraEmOrdemComOsIds() {
        long sessao = sessao(professor, INICIO, null, "SIMULACAO");
        long primeira = mensagem(sessao, "TURMA", "Quando foi o voo?", 1);
        jdbc.update("UPDATE mensagem SET resposta_bloqueada = true WHERE id = ?", primeira);
        jdbc.update("INSERT INTO evento_sessao (sessao_id, tipo, ocorrido_em) VALUES (?, 'REINICIO', ?)",
                sessao, INICIO.plusSeconds(2).atOffset(ZoneOffset.UTC));
        long segunda = mensagem(sessao, "TURMA", "Onde foi o voo?", 3);
        long resposta = mensagem(sessao, "IA", "Em Paris.", 4);

        assertThat(trechos.mensagens(sessao))
                .containsExactly(
                        new MensagemDaRevisao(primeira, true, "Quando foi o voo?", INICIO.plusSeconds(1), true),
                        new MensagemDaRevisao(segunda, true, "Onde foi o voo?", INICIO.plusSeconds(3), false),
                        new MensagemDaRevisao(resposta, false, "Em Paris.", INICIO.plusSeconds(4), false));
        assertThat(trechos.mensagens(sessao).getLast().daIa()).isTrue();
        assertThat(trechos.mensagens(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void sessaoVemComOTituloDaVersaoEATurma() {
        Instant fim = INICIO.plus(Duration.ofMinutes(40));
        long sessao = sessao(professor, INICIO, fim, "SIMULACAO");
        String rotulo = jdbc.queryForObject("SELECT rotulo FROM turma WHERE id = ?", String.class, turma);

        SessaoParaRevisao revisao = trechos.sessao(sessao).orElseThrow();

        assertThat(revisao).isEqualTo(
                new SessaoParaRevisao(sessao, professor, versao, "O voo do 14-bis", rotulo, INICIO, fim));
        assertThat(revisao.encerrada()).isTrue();
        assertThat(trechos.sessao(Long.MAX_VALUE)).isEmpty();
    }

    /**
     * Entram as encerradas desde o momento dado e as abertas que começaram desde ele, da mais recente para
     * a mais antiga; ficam de fora as mais antigas, as de controle e as de outro professor.
     */
    @Test
    void sessoesRecentesDoProfessor() {
        Instant desde = AGORA.minus(Duration.ofDays(7));
        long encerradaOntem = sessao(professor, AGORA.minus(Duration.ofDays(1)), AGORA.minus(Duration.ofHours(23)),
                "SIMULACAO");
        long aberta = sessao(professor, AGORA.minus(Duration.ofHours(1)), null, "SIMULACAO");
        long encerradaNoLimite = sessao(professor, desde.minus(Duration.ofHours(1)), desde, "SIMULACAO");
        sessao(professor, AGORA.minus(Duration.ofDays(9)), AGORA.minus(Duration.ofDays(8)), "SIMULACAO");
        sessao(professor, AGORA.minus(Duration.ofDays(8)), null, "SIMULACAO");
        sessao(professor, AGORA.minus(Duration.ofHours(3)), AGORA.minus(Duration.ofHours(2)), "CONTROLE");
        long outro = dados.idDe(dados.usuario("PROFESSOR"));
        sessao(outro, AGORA.minus(Duration.ofHours(1)), null, "SIMULACAO");

        assertThat(trechos.doProfessorDesde(professor, desde))
                .extracting(SessaoParaRevisao::id)
                .containsExactly(aberta, encerradaOntem, encerradaNoLimite);
    }

    @Test
    void curadorLeAsConversas() {
        long sessao = sessao(professor, INICIO, null, "SIMULACAO");
        mensagem(sessao, "TURMA", "Quando foi o voo?", 1);
        entrar("ROLE_CURADOR");

        assertThat(trechos.mensagens(sessao)).hasSize(1);
    }

    @Test
    void administradorNaoLeAsConversas() {
        entrar("ROLE_ADMINISTRADOR");

        assertThatThrownBy(() -> trechos.mensagens(1)).isInstanceOf(AccessDeniedException.class);
    }

    private long sessao(long professorId, Instant iniciadaEm, Instant encerradaEm, String tipo) {
        // Turmas diferentes, porque só uma sessão pode estar em andamento por turma (RN-10).
        return jdbc.queryForObject(
                """
                INSERT INTO sessao (professor_id, turma_id, cenario_versao_id, limite_perguntas, tipo, status,
                                    iniciada_em, encerrada_em)
                VALUES (?, ?, ?, 40, ?, ?, ?, ?) RETURNING id""",
                Long.class,
                professorId,
                encerradaEm == null ? dados.turma() : turma,
                versao,
                tipo,
                encerradaEm == null ? "EM_ANDAMENTO" : "ENCERRADA",
                iniciadaEm.atOffset(ZoneOffset.UTC),
                encerradaEm == null ? null : encerradaEm.atOffset(ZoneOffset.UTC));
    }

    private long mensagem(long sessao, String autoria, String texto, int segundos) {
        return mensagem(sessao, autoria, texto, segundos, null);
    }

    private long mensagem(long sessao, String autoria, String texto, int segundos, Long meta) {
        return jdbc.queryForObject(
                """
                INSERT INTO mensagem (sessao_id, autoria, texto, resposta_ia_meta_id, criado_em)
                VALUES (?, ?, ?, ?, ?) RETURNING id""",
                Long.class, sessao, autoria, texto, meta, INICIO.plusSeconds(segundos).atOffset(ZoneOffset.UTC));
    }

    private static void entrar(String perfil) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("pessoa@escola.test", null, perfil));
    }
}
