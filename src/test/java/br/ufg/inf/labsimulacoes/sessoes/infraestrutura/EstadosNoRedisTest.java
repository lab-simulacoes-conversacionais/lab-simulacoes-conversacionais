package br.ufg.inf.labsimulacoes.sessoes.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EventoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Estado quente no Redis real: uma chave por sessão, gravada só depois da confirmação (Stack, seção 5). */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
class EstadosNoRedisTest {

    private static final Instant INICIO = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private EstadosDasSessoes estados;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private TransactionTemplate transacao;

    private long sessaoId;

    @BeforeEach
    void escolherSessao() {
        sessaoId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }

    @AfterEach
    void apagarChave() {
        redis.delete(EstadosNoRedis.chave(sessaoId));
    }

    @Test
    void estadoGravadoEhLidoIgual() {
        EstadoDaSessao estado = EstadoDaSessao.inicial(Sessao.iniciar(1, 2, 3, 40, INICIO).comId(sessaoId))
                .registrarMensagem(new MensagemExibida(MensagemExibida.Autoria.TURMA, "Por que voar?", INICIO.plusSeconds(5)))
                .aplicar(EventoDaSessao.pausa(MotivoDaPausa.CONTEUDO_BLOQUEADO, INICIO.plusSeconds(10)))
                .aplicar(EventoDaSessao.retomada(INICIO.plusSeconds(12)))
                .aplicar(EventoDaSessao.reinicio(INICIO.plusSeconds(20)));

        estados.gravar(estado);

        assertThat(estados.buscar(sessaoId)).contains(estado);
    }

    @Test
    void chaveTemValidade() {
        estados.gravar(inicial());

        assertThat(redis.getExpire(EstadosNoRedis.chave(sessaoId))).isPositive();
    }

    @Test
    void semChaveVemVazio() {
        assertThat(estados.buscar(sessaoId)).isEmpty();
    }

    /** O Redis nunca mostra uma mudança que o PostgreSQL desfez. */
    @Test
    void dentroDaTransacaoSoGravaDepoisDaConfirmacao() {
        transacao.executeWithoutResult(status -> {
            estados.gravar(inicial());
            assertThat(estados.buscar(sessaoId)).isEmpty();
        });

        assertThat(estados.buscar(sessaoId)).contains(inicial());
    }

    @Test
    void transacaoDesfeitaNaoGrava() {
        transacao.executeWithoutResult(status -> {
            estados.gravar(inicial());
            status.setRollbackOnly();
        });

        assertThat(estados.buscar(sessaoId)).isEmpty();
    }

    /** Chave ilegível vira vazio, e o estado é refeito a partir do banco. */
    @Test
    void chaveIlegivelVemVazia() {
        redis.opsForValue().set(EstadosNoRedis.chave(sessaoId), "{isto não é o estado");

        assertThat(estados.buscar(sessaoId)).isEmpty();
    }

    /** RN-20: a marca da pergunta com resposta bloqueada sobrevive à ida e volta pelo Redis. */
    @Test
    void perguntaComRespostaBloqueadaEhLidaIgual() {
        EstadoDaSessao estado = inicial()
                .registrarMensagem(new MensagemExibida(MensagemExibida.Autoria.TURMA, "Oi", INICIO.plusSeconds(5), true));

        estados.gravar(estado);

        EstadoDaSessao lido = estados.buscar(sessaoId).orElseThrow();
        assertThat(lido).isEqualTo(estado);
        assertThat(lido.perguntaPendente()).isEmpty();
    }

    /** Uma chave gravada antes de a marca existir continua legível, sem a marca. */
    @Test
    void chaveSemAMarcaDeBloqueioEhLida() {
        redis.opsForValue().set(EstadosNoRedis.chave(sessaoId), """
                {"sessaoId": %d, "status": "EM_ANDAMENTO", "motivoDaPausa": null, "iniciadaEm": "2026-11-11T13:00:00Z",
                 "tempoEmPausaNanos": 0, "pausadaDesde": null, "reiniciadaEm": null, "perguntasDaTurma": 1,
                 "ultimasMensagens": [{"autoria": "TURMA", "texto": "Oi", "em": "2026-11-11T13:00:05Z"}]}"""
                .formatted(sessaoId));

        EstadoDaSessao lido = estados.buscar(sessaoId).orElseThrow();

        assertThat(lido.ultimasMensagens()).extracting(MensagemExibida::respostaBloqueada).containsExactly(false);
        assertThat(lido.perguntaPendente()).isPresent();
    }

    private EstadoDaSessao inicial() {
        return EstadoDaSessao.inicial(Sessao.iniciar(1, 2, 3, 40, INICIO).comId(sessaoId));
    }
}
