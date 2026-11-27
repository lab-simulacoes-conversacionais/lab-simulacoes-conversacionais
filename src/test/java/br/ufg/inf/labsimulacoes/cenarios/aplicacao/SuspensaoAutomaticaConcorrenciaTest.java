package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.GravidadeDoErro;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerfilDeQuemSinalizou;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SinalizacaoDaVersao;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Duas triagens moderadas do mesmo cenário ao mesmo tempo (RN-23c): sem a trava do cenário, cada uma veria só
 * a própria e a primeira, e nenhuma suspenderia. Com a trava, a segunda espera a primeira confirmar e suspende
 * uma vez só.
 *
 * <p>A sobreposição é forçada, sem esperar por tempo: a primeira triagem segura a transação aberta até o
 * PostgreSQL mostrar a segunda parada na trava. Sem {@code @Transactional}, para cada triagem confirmar a
 * própria transação como em produção.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
class SuspensaoAutomaticaConcorrenciaTest {

    private static final AtomicLong SINALIZACAO = new AtomicLong(System.nanoTime());
    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Autowired
    private SuspensaoAutomatica suspensao;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private MeterRegistry metricas;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void duasTriagensModeradasAoMesmoTempoSuspendemUmaVezSo() throws Exception {
        long autora = inserirUsuario("autora");
        long curadora = inserirUsuario("curadora");
        Instant momento = Instant.now().truncatedTo(ChronoUnit.MICROS);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
        Cenario rascunho = cenarios.salvar(Cenario.novo(autora, ConteudosDeTeste.completo()));
        Cenario aprovado = cenarios.salvar(cenarios.salvar(rascunho.enviarACuradoria(autora))
                .aprovar(new Revisor(curadora, true), ConteudosDeTeste.CONFERIDA, momento));
        long cenario = aprovado.id();
        long versao = aprovado.versao().id();

        // Um moderado já confirmado; faltam dois para o RN-23c.
        long primeira = SINALIZACAO.incrementAndGet();
        long segunda = SINALIZACAO.incrementAndGet();
        long terceira = SINALIZACAO.incrementAndGet();
        for (long id : List.of(primeira, segunda, terceira)) {
            suspensao.registrar(SinalizacaoDaVersao.pendente(
                    id, versao, PerfilDeQuemSinalizou.CURADOR, GravidadeDoErro.MODERADA, momento), curadora);
        }
        suspensao.triar(moderada(primeira, versao, curadora, momento));
        double antes = contador();

        CountDownLatch primeiraTravou = new CountDownLatch(1);
        CountDownLatch liberarPrimeira = new CountDownLatch(1);
        ExecutorService triagens = Executors.newFixedThreadPool(2);
        try {
            Future<?> daPrimeira = triagens.submit(() -> transacao.executeWithoutResult(status -> {
                suspensao.triar(moderada(segunda, versao, curadora, momento));
                primeiraTravou.countDown();
                aguardar(liberarPrimeira);
            }));
            assertThat(primeiraTravou.await(20, TimeUnit.SECONDS)).isTrue();

            Future<?> daSegunda = triagens.submit(() -> suspensao.triar(moderada(terceira, versao, curadora, momento)));
            await().atMost(ESPERA).until(this::alguemEsperaPelaTrava);
            assertThat(status(cenario)).as("a primeira ainda não confirmou").isEqualTo("APROVADO");

            liberarPrimeira.countDown();
            daPrimeira.get(20, TimeUnit.SECONDS);
            daSegunda.get(20, TimeUnit.SECONDS);
        } finally {
            liberarPrimeira.countDown();
            triagens.shutdownNow();
        }

        assertThat(status(cenario)).isEqualTo("SUSPENSO");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE event_type LIKE '%.CenarioSuspenso' AND listener_id LIKE '%OuvinteDeAcoesCriticas%'
                  AND serialized_event LIKE ?""", Integer.class, "%\"cenarioId\":" + cenario + ",%"))
                .isEqualTo(1);
        assertThat(contador()).isEqualTo(antes + 1);
    }

    /** Uma conexão do banco parada esperando a trava de outra transação. */
    private boolean alguemEsperaPelaTrava() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND wait_event_type = 'Lock'""", Integer.class) > 0;
    }

    private static SinalizacaoDaVersao moderada(long id, long versao, long curadora, Instant momento) {
        return SinalizacaoDaVersao.confirmada(
                id, versao, PerfilDeQuemSinalizou.CURADOR, GravidadeDoErro.MODERADA, GravidadeDoErro.MODERADA,
                curadora, momento, momento);
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("A segunda triagem não chegou à trava");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String status(long cenario) {
        return jdbc.queryForObject("SELECT status FROM cenario WHERE id = ?", String.class, cenario);
    }

    /** Chamado fora de transação, o contador sobe na hora; dentro dela, na confirmação. */
    private double contador() {
        Counter contador = metricas.find(SuspensaoAutomatica.METRICA_DA_SUSPENSAO)
                .tag("motivo", "tres_erros_moderados")
                .counter();
        return contador == null ? 0 : contador.count();
    }

    private long inserirUsuario(String prefixo) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id",
                Long.class,
                prefixo + "-" + UUID.randomUUID() + "@escola.test");
    }
}
