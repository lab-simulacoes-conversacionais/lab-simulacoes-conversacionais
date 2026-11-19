package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PedidosDeSugestao;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Contagem dos pedidos de sugestão no Redis real (RNF-13). */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
class PedidosDeSugestaoNoRedisTest {

    private static final Duration UMA_HORA = Duration.ofHours(1);

    @Autowired
    private PedidosDeSugestao pedidos;

    @Autowired
    private StringRedisTemplate redis;

    private long usuarioId;

    @BeforeEach
    void escolherUsuario() {
        usuarioId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }

    @AfterEach
    void apagarChave() {
        redis.delete(PedidosDeSugestaoNoRedis.chave(usuarioId));
    }

    @Test
    void cadaPedidoSomaUm() {
        assertThat(pedidos.registrar(usuarioId, UMA_HORA)).isEqualTo(1);
        assertThat(pedidos.registrar(usuarioId, UMA_HORA)).isEqualTo(2);
        assertThat(pedidos.registrar(usuarioId, UMA_HORA)).isEqualTo(3);
    }

    @Test
    void cadaUsuarioTemAPropriaContagem() {
        pedidos.registrar(usuarioId, UMA_HORA);
        long outro = usuarioId - 1;
        try {
            assertThat(pedidos.registrar(outro, UMA_HORA)).isEqualTo(1);
        } finally {
            redis.delete(PedidosDeSugestaoNoRedis.chave(outro));
        }
    }

    /** A janela começa no primeiro pedido, e os seguintes não a estendem. */
    @Test
    void chaveTemAValidadeDaJanelaDesdeOPrimeiroPedido() {
        pedidos.registrar(usuarioId, UMA_HORA);
        redis.expire(PedidosDeSugestaoNoRedis.chave(usuarioId), Duration.ofMinutes(5));

        pedidos.registrar(usuarioId, UMA_HORA);

        assertThat(redis.getExpire(PedidosDeSugestaoNoRedis.chave(usuarioId))).isPositive().isLessThanOrEqualTo(300);
    }

    @Test
    void depoisDaJanelaAContagemRecomeca() {
        pedidos.registrar(usuarioId, UMA_HORA);
        pedidos.registrar(usuarioId, UMA_HORA);
        redis.delete(PedidosDeSugestaoNoRedis.chave(usuarioId));

        assertThat(pedidos.registrar(usuarioId, UMA_HORA)).isEqualTo(1);
    }

    /** Falha do Redis não impede a sugestão: o pedido passa sem contar. */
    @Test
    void falhaDoRedisDeixaOPedidoPassar() {
        StringRedisTemplate foraDoAr = mock(StringRedisTemplate.class);
        when(foraDoAr.opsForValue()).thenThrow(new RedisConnectionFailureException("fora do ar"));

        assertThat(new PedidosDeSugestaoNoRedis(foraDoAr).registrar(usuarioId, UMA_HORA)).isZero();
    }
}
