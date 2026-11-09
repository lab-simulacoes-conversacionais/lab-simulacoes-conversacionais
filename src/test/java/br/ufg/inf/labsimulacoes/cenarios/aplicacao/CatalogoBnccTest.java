package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.CatalogoBncc;
import br.ufg.inf.labsimulacoes.cenarios.ConsultaAoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.HabilidadeDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ResultadoDaImportacao;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Critérios de aceite da issue #8 com o arquivo oficial real, no PostgreSQL e no Redis de verdade.
 * Cada teste desfaz o que gravou. O cache é provado no {@link CacheDoCatalogoTest}.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class CatalogoBnccTest {

    @Autowired
    private ImportacaoDoCatalogo importacao;

    @Autowired
    private CatalogoBncc catalogo;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CacheManager caches;

    /**
     * O banco volta ao estado anterior no fim de cada teste, mas o Redis não participa da transação.
     * Sem isto, uma busca guardada aqui seria atendida em outro teste com um catálogo que já não existe.
     */
    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
        caches.getCache(CacheDoCatalogoTest.CACHE).clear();
    }

    @Test
    void buscaPorEF08HI03RetornaAHabilidadeCorreta() {
        importacao.importar();
        comPerfis("ROLE_PROFESSOR");

        List<HabilidadeDoCatalogo> resultado = catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF08HI03"));

        assertThat(resultado).singleElement().satisfies(habilidade -> {
            assertThat(habilidade.codigo()).isEqualTo("EF08HI03");
            assertThat(habilidade.descricao()).isEqualTo(
                    "Analisar os impactos da Revolução Industrial na produção e circulação de povos, produtos e culturas.");
            assertThat(habilidade.etapa()).isEqualTo("EF");
            assertThat(habilidade.anos()).containsExactly(8);
            assertThat(habilidade.componente()).isEqualTo("História");
        });
        assertThat(linha("EF08HI03")).containsEntry("ano", "8").containsEntry("etapa", "EF")
                .containsEntry("componente", "História");
    }

    @Test
    void reimportarNaoDuplicaRegistros() {
        int antes = contar();

        ResultadoDaImportacao primeira = importacao.importar();
        int depoisDaPrimeira = contar();
        ResultadoDaImportacao segunda = importacao.importar();
        int depoisDaSegunda = contar();

        assertThat(primeira).isEqualTo(new ResultadoDaImportacao(1304, 0, 0, 0));
        assertThat(segunda).isEqualTo(new ResultadoDaImportacao(0, 0, 1304, 0));
        assertThat(depoisDaPrimeira).isEqualTo(antes + 1304);
        assertThat(depoisDaSegunda).isEqualTo(depoisDaPrimeira);
    }

    @Test
    void reimportarCorrigeOQueMudouNoBanco() {
        importacao.importar();
        jdbc.update("UPDATE habilidade_bncc SET descricao = 'alterada à mão' WHERE codigo = 'EF08HI03'");

        assertThat(importacao.importar()).isEqualTo(new ResultadoDaImportacao(0, 1, 1303, 0));
        assertThat(linha("EF08HI03").get("descricao")).asString().startsWith("Analisar os impactos");
    }

    @Test
    void habilidadeDeBlocoDeAnosEEncontradaPorCadaAnoDoBloco() {
        importacao.importar();
        comPerfis("ROLE_CURADOR");

        assertThat(linha("EF67LP08")).containsEntry("ano", "6,7");
        assertThat(codigos(ConsultaAoCatalogo.porCodigo("EF67LP08").comAno(7))).containsExactly("EF67LP08");
        assertThat(codigos(ConsultaAoCatalogo.porCodigo("EF67LP08").comAno(6))).containsExactly("EF67LP08");
        assertThat(codigos(ConsultaAoCatalogo.porCodigo("EF67LP08").comAno(8))).isEmpty();
    }

    @Test
    void codigoSemDiferenciarMaiusculasEComEspacos() {
        importacao.importar();
        comPerfis("ROLE_PROFESSOR");

        assertThat(codigos(ConsultaAoCatalogo.porCodigo("  ef08hi03 "))).containsExactly("EF08HI03");
    }

    @Test
    void filtrosCombinadosNoCatalogoReal() {
        importacao.importar();
        comPerfis("ROLE_PROFESSOR");

        var consulta = ConsultaAoCatalogo.todas()
                .comDescricao("revolução industrial")
                .comComponente("história")
                .comEtapa("ef")
                .comAno(8);

        assertThat(codigos(consulta)).contains("EF08HI03")
                .allSatisfy(codigo -> assertThat(codigo).startsWith("EF08HI"));
    }

    @Test
    void resultadoVemEmOrdemDeCodigoComLimitePadraoDeCinquenta() {
        importacao.importar();
        comPerfis("ROLE_PROFESSOR");

        List<String> linguaPortuguesa = codigos(ConsultaAoCatalogo.todas().comComponente("Língua Portuguesa"));

        assertThat(linguaPortuguesa).hasSize(50).isSorted();
        assertThat(codigos(ConsultaAoCatalogo.todas().comComponente("Língua Portuguesa").comLimite(500))).hasSize(391);
    }

    @Test
    void semLoginABuscaERecusada() {
        assertThatThrownBy(() -> catalogo.buscar(ConsultaAoCatalogo.porCodigo("EF08HI03")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    /** O catálogo é dado de referência: ler não é editar conteúdo pedagógico (RN-37). */
    @ParameterizedTest
    @ValueSource(strings = {"ROLE_PROFESSOR", "ROLE_CURADOR", "ROLE_ADMINISTRADOR"})
    void qualquerPerfilAutenticadoBusca(String perfil) {
        importacao.importar();
        comPerfis(perfil);

        assertThat(codigos(ConsultaAoCatalogo.porCodigo("EF08HI03"))).containsExactly("EF08HI03");
    }

    /** A importação roda na subida, sem ninguém logado. */
    @Test
    void importacaoNaoExigeUsuarioLogado() {
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        assertThat(importacao.importar().inseridas()).isEqualTo(1304);
    }

    private List<String> codigos(ConsultaAoCatalogo consulta) {
        return catalogo.buscar(consulta).stream().map(HabilidadeDoCatalogo::codigo).toList();
    }

    private Map<String, Object> linha(String codigo) {
        return jdbc.queryForMap("SELECT descricao, etapa, ano, componente FROM habilidade_bncc WHERE codigo = ?", codigo);
    }

    private int contar() {
        return jdbc.queryForObject("SELECT count(*) FROM habilidade_bncc", Integer.class);
    }

    private static void comPerfis(String... papeis) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("usuario@escola.br", null, papeis));
    }
}
