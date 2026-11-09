package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Spring Cache no Redis (Stack, seção 5). O valor é gravado com a serialização do Java, então o que
 * vai para o cache precisa ser {@link java.io.Serializable}.
 *
 * <p>A validade é só uma garantia a mais: o importador limpa o cache ao terminar, e o catálogo não
 * muda de outro jeito.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class ConfiguracaoDoCache {

    /** As chaves ficam no Redis como {@code habilidades-bncc::<filtro>}. */
    static final String HABILIDADES_BNCC = "habilidades-bncc";

    @Bean
    RedisCacheManagerBuilderCustomizer cacheDasHabilidadesBncc(
            @Value("${bncc.cache.validade:24h}") Duration validade) {
        var serializacao = new JdkSerializationRedisSerializer(ConfiguracaoDoCache.class.getClassLoader());
        return construtor -> construtor.withCacheConfiguration(HABILIDADES_BNCC,
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(validade)
                        .disableCachingNullValues()
                        .serializeValuesWith(SerializationPair.fromSerializer(serializacao)));
    }
}
