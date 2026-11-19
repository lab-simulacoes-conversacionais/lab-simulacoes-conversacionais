package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.PedidosDeSugestao;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Uma chave por usuário, {@code sugestao:<id>:pedidos}, com a contagem da janela atual. O incremento
 * e a validade vão num script Lua só, que o Redis roda de forma atômica: se a chave não tem validade
 * (acabou de ser criada pelo incremento), ela recebe a da janela. Com dois comandos separados, a chave
 * podia vencer entre um e outro, e o incremento criava uma contagem sem validade, que bloqueava o
 * professor para sempre.
 *
 * <p>Falha do Redis não impede o professor de pedir a sugestão: o pedido passa sem contar, e o aviso
 * fica no log.
 */
@Component
class PedidosDeSugestaoNoRedis implements PedidosDeSugestao {

    private static final Logger LOG = LoggerFactory.getLogger(PedidosDeSugestaoNoRedis.class);

    private static final RedisScript<Long> CONTAR = RedisScript.of("""
            local pedidos = redis.call('INCR', KEYS[1])
            if redis.call('TTL', KEYS[1]) == -1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return pedidos
            """, Long.class);

    private final StringRedisTemplate redis;

    PedidosDeSugestaoNoRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String chave(long usuarioId) {
        return "sugestao:" + usuarioId + ":pedidos";
    }

    @Override
    public long registrar(long usuarioId, Duration janela) {
        String chave = chave(usuarioId);
        try {
            Long pedidos = redis.execute(CONTAR, List.of(chave), String.valueOf(janela.toSeconds()));
            return pedidos == null ? 0 : pedidos;
        } catch (RuntimeException e) {
            LOG.warn("Não deu para contar o pedido de sugestão do usuário {} no Redis", usuarioId, e);
            return 0;
        }
    }
}
