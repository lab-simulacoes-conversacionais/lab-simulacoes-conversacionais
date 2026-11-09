package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.CatalogoBncc;
import br.ufg.inf.labsimulacoes.cenarios.ConsultaAoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.HabilidadeDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.FiltroDeHabilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cache das buscas no Redis real (Stack, seção 5). O spy no repositório conta quantas vezes a busca
 * chegou ao PostgreSQL.
 *
 * <p>Sem {@code @Transactional}: a limpeza do cache só acontece depois da confirmação, então os testes
 * precisam confirmar de verdade. Como o spy cria um contexto só para esta classe, com banco e Redis
 * próprios, o catálogo gravado aqui não chega aos outros testes.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
class CacheDoCatalogoTest {

    static final String CACHE = "habilidades-bncc";

    @MockitoSpyBean
    private Habilidades habilidades;

    @Autowired
    private CatalogoBncc catalogo;

    @Autowired
    private ImportacaoDoCatalogo importacao;

    @Autowired
    private CacheManager caches;

    @Autowired
    private RedisConnectionFactory redis;

    @Autowired
    private PlatformTransactionManager transacoes;

    @BeforeEach
    void catalogoImportadoECacheVazio() {
        importacao.importar();
        caches.getCache(CACHE).clear();
        clearInvocations(habilidades);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("professora@escola.br", null, "ROLE_PROFESSOR"));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void segundaBuscaIgualEAtendidaPeloCache() {
        List<HabilidadeDoCatalogo> primeira = catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF08HI03"));
        List<HabilidadeDoCatalogo> segunda = catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF08HI03"));

        assertThat(segunda).isEqualTo(primeira).extracting(HabilidadeDoCatalogo::codigo).containsExactly("EF08HI03");
        verify(habilidades, times(1)).buscar(any());
    }

    @Test
    void buscaQueSoMudaEmMaiusculasOuEspacosCaiNaMesmaChave() {
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF08HI03").comComponente("História"));
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("  ef08hi03 ").comComponente(" HISTÓRIA").comEtapa(" "));

        verify(habilidades, times(1)).buscar(any());
    }

    @Test
    void buscaDiferenteVaiAoBanco() {
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF08HI03"));
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF67LP08"));
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF08HI03").comLimite(10));

        verify(habilidades, times(3)).buscar(any());
    }

    @Test
    void buscaSemResultadoTambemFicaEmCache() {
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF99XX99"));
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF99XX99"));

        verify(habilidades, times(1)).buscar(any());
    }

    /** O valor fica no Redis com a serialização do Java, com validade, e volta igual. */
    @Test
    void valorGuardadoNoRedisESerializavel() {
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF67LP08"));
        byte[] chave = chave(new FiltroDeHabilidades("EF67LP08", null, null, null, null, 50));

        try (RedisConnection conexao = redis.getConnection()) {
            byte[] valor = conexao.stringCommands().get(chave);
            assertThat(valor).isNotNull();
            assertThat(new JdkSerializationRedisSerializer().deserialize(valor)).isEqualTo(List.of(
                    new HabilidadeBncc("EF67LP08", descricao("EF67LP08"), "EF", List.of(6, 7), "Língua Portuguesa")));
            assertThat(conexao.keyCommands().ttl(chave)).isPositive();
        }
    }

    @Test
    void importarLimpaOCache() {
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF08HI03"));
        catalogo.buscar(ConsultaAoCatalogo.todas().comAno(7));
        assertThat(chavesNoCache()).hasSize(2);

        importacao.importar();

        assertThat(chavesNoCache()).isEmpty();
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF08HI03"));
        verify(habilidades, times(3)).buscar(any());
    }

    /**
     * Se o cache fosse limpo antes da confirmação, uma busca nesse meio-tempo leria o catálogo
     * antigo e o guardaria de novo. Por isso a chave só some depois do commit.
     */
    @Test
    void cacheSoELimpoDepoisQueAImportacaoConfirma() {
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF08HI03"));

        new TransactionTemplate(transacoes).executeWithoutResult(status -> {
            importacao.importar();
            assertThat(chavesNoCache()).as("antes do commit").hasSize(1);
        });

        assertThat(chavesNoCache()).as("depois do commit").isEmpty();
    }

    /** Se a gravação foi desfeita, o catálogo não mudou e o cache continua valendo. */
    @Test
    void importacaoDesfeitaNaoLimpaOCache() {
        catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF08HI03"));

        new TransactionTemplate(transacoes).executeWithoutResult(status -> {
            importacao.importar();
            status.setRollbackOnly();
        });

        assertThat(chavesNoCache()).hasSize(1);
    }

    private List<String> chavesNoCache() {
        try (RedisConnection conexao = redis.getConnection()) {
            var chaves = conexao.keyCommands().keys((CACHE + "::*").getBytes(StandardCharsets.UTF_8));
            return chaves.stream().map(chave -> new String(chave, StandardCharsets.UTF_8)).toList();
        }
    }

    private static byte[] chave(FiltroDeHabilidades filtro) {
        return (CACHE + "::" + filtro.chave()).getBytes(StandardCharsets.UTF_8);
    }

    private String descricao(String codigo) {
        return catalogo.buscar(ConsultaAoCatalogo.porCodigo(codigo)).getFirst().descricao();
    }
}
