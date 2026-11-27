package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.eventos.CenarioSuspenso;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao.VersaoNoBanco;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RN-25: o cenário suspenso pausa as sessões em andamento dele, com PostgreSQL e Redis reais. O evento é
 * publicado como Cenários publica, e o ouvinte de Sessões roda depois da transação, em outra thread.
 *
 * <p>Sem {@code @Transactional}, porque o ouvinte só recebe o evento depois da confirmação e o Redis só é gravado
 * depois dela. Roda no contexto dos testes da RF-25, porque grava versões aprovadas (RN-08).
 */
@SpringBootTest(properties = "lab.teste.contexto=suspensao")
@Import(InfraestruturaDeTeste.class)
class PausaPorSuspensaoTest {

    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Autowired
    private ApplicationEventPublisher eventos;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private Sessoes sessoes;

    @Autowired
    private EstadosDasSessoes estados;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private long professora;
    private long curadora;
    private VersaoNoBanco cenario;

    @BeforeEach
    void prepararCenarioAprovado() {
        dados = new DadosDeSessao(jdbc);
        professora = dados.idDe(dados.usuario("PROFESSOR"));
        curadora = dados.idDe(dados.usuario("CURADOR"));
        cenario = dados.cenarioAprovado(professora, curadora);
    }

    /** Todas as em andamento do cenário pausam, inclusive a que começou com uma versão aprovada anterior. */
    @Test
    void pausaAsSessoesEmAndamentoDoCenarioEmQualquerVersao() {
        long anterior = cenario.versaoId();
        long vigente = versaoAprovada(cenario.cenarioId(), 2);
        long naAnterior = sessao(anterior, "EM_ANDAMENTO", null, Duration.ofMinutes(10));
        long naVigente = sessao(vigente, "EM_ANDAMENTO", null, Duration.ofMinutes(5));
        Instant antes = Instant.now().truncatedTo(ChronoUnit.MICROS);

        suspender(cenario.cenarioId(), vigente);

        for (long sessao : List.of(naAnterior, naVigente)) {
            assertThat(statusEMotivo(sessao)).containsEntry("status", "PAUSADA")
                    .containsEntry("motivo_pausa", "CENARIO_SUSPENSO");
            assertThat(pausas(sessao)).singleElement().satisfies(pausa -> {
                assertThat(pausa).containsEntry("motivo", "CENARIO_SUSPENSO");
                // O momento é o da pausa, e não o da suspensão, que veio no evento com um dia de atraso.
                assertThat(((Timestamp) pausa.get("ocorrido_em")).toInstant()).isAfterOrEqualTo(antes);
            });
            EstadoDaSessao noRedis = estados.buscar(sessao).orElseThrow();
            assertThat(noRedis.status()).isEqualTo(StatusDaSessao.PAUSADA);
            assertThat(noRedis.motivoDaPausa()).isEqualTo(MotivoDaPausa.CENARIO_SUSPENSO);
        }
    }

    /** A pausada continua com o motivo dela, a encerrada não muda, e a sessão de outro cenário segue. */
    @Test
    void naoMexeNaPausadaNaEncerradaNemEmOutroCenario() {
        long pausada = sessao(cenario.versaoId(), "PAUSADA", "MANUAL", Duration.ofMinutes(10));
        long encerrada = sessao(cenario.versaoId(), "ENCERRADA", null, Duration.ofMinutes(60));
        VersaoNoBanco outro = dados.cenarioAprovado(professora, curadora);
        long deOutroCenario = sessao(outro.versaoId(), "EM_ANDAMENTO", null, Duration.ofMinutes(10));
        long doCenario = sessao(cenario.versaoId(), "EM_ANDAMENTO", null, Duration.ofMinutes(10));

        assertThat(sessoes.emAndamentoDoCenario(cenario.cenarioId())).containsExactly(doCenario);
        suspender(cenario.cenarioId(), cenario.versaoId());

        assertThat(statusEMotivo(doCenario)).containsEntry("motivo_pausa", "CENARIO_SUSPENSO");
        assertThat(statusEMotivo(pausada)).containsEntry("status", "PAUSADA").containsEntry("motivo_pausa", "MANUAL");
        assertThat(statusEMotivo(encerrada)).containsEntry("status", "ENCERRADA");
        assertThat(statusEMotivo(deOutroCenario)).containsEntry("status", "EM_ANDAMENTO");
        assertThat(pausas(pausada)).isEmpty();
        assertThat(pausas(encerrada)).isEmpty();
        assertThat(pausas(deOutroCenario)).isEmpty();
    }

    /**
     * A suspensão entregue de novo, depois da primeira entrega, encontra a sessão já pausada e não grava outra
     * pausa.
     */
    @Test
    void reentregaNaoPausaDeNovo() {
        long sessao = sessao(cenario.versaoId(), "EM_ANDAMENTO", null, Duration.ofMinutes(10));
        var evento = suspensao(cenario.cenarioId(), cenario.versaoId());

        publicar(evento);
        aguardarOuvinte(cenario.cenarioId(), 1);
        publicar(evento);

        aguardarOuvinte(cenario.cenarioId(), 2);
        assertThat(statusEMotivo(sessao)).containsEntry("motivo_pausa", "CENARIO_SUSPENSO");
        assertThat(pausas(sessao)).hasSize(1);
    }

    /**
     * Passados os 50 minutos, a pausa do limite entra antes, no momento dos 50 minutos, e o histórico fica em
     * ordem (RN-17). A sessão já pausada por isso não pausa uma segunda vez.
     */
    @Test
    void depoisDosCinquentaMinutosAPausaDoLimiteEntraAntes() {
        long sessao = sessao(cenario.versaoId(), "EM_ANDAMENTO", null, Duration.ofMinutes(55));

        suspender(cenario.cenarioId(), cenario.versaoId());

        assertThat(statusEMotivo(sessao)).containsEntry("motivo_pausa", "LIMITE_50_MINUTOS");
        assertThat(pausas(sessao)).singleElement().satisfies(pausa -> assertThat(pausa)
                .containsEntry("motivo", "LIMITE_50_MINUTOS"));
        assertThat(estados.buscar(sessao).orElseThrow().motivoDaPausa()).isEqualTo(MotivoDaPausa.LIMITE_50_MINUTOS);
    }

    private void suspender(long cenarioId, long versaoId) {
        publicar(suspensao(cenarioId, versaoId));
        aguardarOuvinte(cenarioId, 1);
    }

    /** Com um dia de atraso no momento, para mostrar que a pausa usa o relógio da sessão. */
    private CenarioSuspenso suspensao(long cenarioId, long versaoId) {
        return new CenarioSuspenso(
                cenarioId, versaoId, professora, "SINALIZACAO_GRAVE", Instant.now().minus(Duration.ofDays(1)));
    }

    private void publicar(Object evento) {
        transacao.executeWithoutResult(status -> eventos.publishEvent(evento));
    }

    private void aguardarOuvinte(long cenarioId, int entregas) {
        await().atMost(ESPERA).until(() -> jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE listener_id LIKE '%OuvinteDeSuspensao%' AND completion_date IS NOT NULL
                  AND serialized_event LIKE ?""", Integer.class, "%\"cenarioId\":" + cenarioId + ",%")
                >= entregas);
    }

    private long sessao(long versaoId, String status, String motivo, Duration haQuanto) {
        Instant iniciada = Instant.now().minus(haQuanto);
        return jdbc.queryForObject("""
                INSERT INTO sessao (professor_id, turma_id, cenario_versao_id, limite_perguntas, status, motivo_pausa,
                                    iniciada_em, encerrada_em)
                VALUES (?, ?, ?, 40, ?, ?, ?, ?) RETURNING id""",
                Long.class,
                professora,
                dados.turma(),
                versaoId,
                status,
                motivo,
                iniciada.atOffset(ZoneOffset.UTC),
                "ENCERRADA".equals(status) ? Instant.now().atOffset(ZoneOffset.UTC) : null);
    }

    /** Uma versão aprovada a mais do mesmo cenário, que passa a ser a vigente (RN-08). */
    private long versaoAprovada(long cenarioId, int numero) {
        long versaoId = jdbc.queryForObject("""
                INSERT INTO cenario_versao (cenario_id, numero, status, titulo, conteudo, criado_por_id,
                                            aprovado_por_id, aprovado_em)
                VALUES (?, ?, 'APROVADA', 'O voo do 14-bis', '{}'::jsonb, ?, ?, now()) RETURNING id""",
                Long.class, cenarioId, numero, professora, curadora);
        jdbc.update("UPDATE cenario SET versao_vigente_id = ? WHERE id = ?", versaoId, cenarioId);
        return versaoId;
    }

    private Map<String, Object> statusEMotivo(long sessao) {
        return jdbc.queryForMap("SELECT status, motivo_pausa FROM sessao WHERE id = ?", sessao);
    }

    private List<Map<String, Object>> pausas(long sessao) {
        return jdbc.queryForList(
                "SELECT motivo, ocorrido_em FROM evento_sessao WHERE sessao_id = ? AND tipo = 'PAUSA'", sessao);
    }
}
