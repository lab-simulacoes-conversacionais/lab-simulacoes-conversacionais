package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.PedidosDeSugestao;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Uma chave por usuário, {@code sugestao:<id>:pedidos}, com a contagem da janela atual. A chave é
 * criada com a validade da janela antes do incremento, e o incremento mantém a validade: assim nenhuma
 * contagem fica sem validade, mesmo se a aplicação cair entre os dois comandos.
 *
 * <p>Falha do Redis não impede o professor de pedir a sugestão: o pedido passa sem contar, e o aviso
 * fica no log.
 */
@Component
class PedidosDeSugestaoNoRedis implements PedidosDeSugestao {

    private static final Logger LOG = LoggerFactory.getLogger(PedidosDeSugestaoNoRedis.class);

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
            redis.opsForValue().setIfAbsent(chave, "0", janela);
            Long pedidos = redis.opsForValue().increment(chave);
            return pedidos == null ? 0 : pedidos;
        } catch (RuntimeException e) {
            LOG.warn("Não deu para contar o pedido de sugestão do usuário {} no Redis", usuarioId, e);
            return 0;
        }
    }
}
