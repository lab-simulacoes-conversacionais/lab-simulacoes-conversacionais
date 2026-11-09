package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.FiltroDeHabilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ResultadoDaGravacao;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Gravação e busca no PostgreSQL real. Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class HabilidadesJdbcTest {

    private static final HabilidadeBncc REVOLUCAO = new HabilidadeBncc("EF08HI03",
            "Analisar os impactos da Revolução Industrial na produção e circulação de povos.", "EF", List.of(8), "História");
    private static final HabilidadeBncc IMAGENS = new HabilidadeBncc("EF67LP08",
            "Identificar os efeitos de sentido devidos à escolha de imagens.", "EF", List.of(6, 7), "Língua Portuguesa");
    private static final HabilidadeBncc TEXTOS = new HabilidadeBncc("EF15LP01",
            "Identificar a função social de textos que circulam em campos da vida social.", "EF",
            List.of(1, 2, 3, 4, 5), "Língua Portuguesa");
    private static final HabilidadeBncc PORCENTAGEM = new HabilidadeBncc("EF09MA05",
            "Resolver problemas com 100% de desconto_simples.", "EF", List.of(9), "Matemática");

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void comecarComCatalogoVazio() {
        jdbc.update("DELETE FROM habilidade_bncc");
    }

    @Test
    void gravarInsereEGuardaOsAnosComoTexto() {
        var resultado = habilidades.gravar(List.of(REVOLUCAO, IMAGENS, TEXTOS));

        assertThat(resultado).isEqualTo(new ResultadoDaGravacao(3, 0));
        assertThat(jdbc.queryForList("SELECT codigo || '=' || ano FROM habilidade_bncc ORDER BY codigo", String.class))
                .containsExactly("EF08HI03=8", "EF15LP01=1,2,3,4,5", "EF67LP08=6,7");
    }

    @Test
    void gravarDeNovoOMesmoNaoDuplicaNemContaComoAtualizacao() {
        habilidades.gravar(List.of(REVOLUCAO, IMAGENS));

        var resultado = habilidades.gravar(List.of(REVOLUCAO, IMAGENS));

        assertThat(resultado).isEqualTo(new ResultadoDaGravacao(0, 0));
        assertThat(contar()).isEqualTo(2);
    }

    @Test
    void habilidadeQueMudouEAtualizadaSemMudarACriacao() {
        habilidades.gravar(List.of(REVOLUCAO, IMAGENS));
        jdbc.update("UPDATE habilidade_bncc SET criado_em = '2026-01-01'");
        var corrigida = new HabilidadeBncc("EF08HI03", "Texto corrigido.", "EF", List.of(8), "História");

        var resultado = habilidades.gravar(List.of(corrigida, IMAGENS, TEXTOS));

        assertThat(resultado).isEqualTo(new ResultadoDaGravacao(1, 1));
        assertThat(jdbc.queryForObject("SELECT descricao FROM habilidade_bncc WHERE codigo = 'EF08HI03'", String.class))
                .isEqualTo("Texto corrigido.");
        assertThat(jdbc.queryForObject(
                "SELECT criado_em = '2026-01-01' FROM habilidade_bncc WHERE codigo = 'EF08HI03'", Boolean.class))
                .isTrue();
    }

    /** As versões de cenário apontam para habilidade_bncc, então nada sai do catálogo. */
    @Test
    void habilidadeQueSumiuDoArquivoContinuaNoBanco() {
        habilidades.gravar(List.of(REVOLUCAO, IMAGENS));

        habilidades.gravar(List.of(IMAGENS));

        assertThat(contar()).isEqualTo(2);
    }

    @Test
    void gravarListaVaziaNaoFazNada() {
        assertThat(habilidades.gravar(List.of())).isEqualTo(new ResultadoDaGravacao(0, 0));
    }

    @Test
    void buscaPorCodigoDevolveAHabilidadeComOsAnos() {
        habilidades.gravar(List.of(REVOLUCAO, IMAGENS));

        assertThat(habilidades.buscar(filtro("EF67LP08", null, null, null, null))).containsExactly(IMAGENS);
    }

    @Test
    void buscaPorDescricaoIgnoraMaiusculas() {
        habilidades.gravar(List.of(REVOLUCAO, IMAGENS, TEXTOS));

        assertThat(habilidades.buscar(filtro(null, "REVOLUÇÃO INDUSTRIAL", null, null, null))).containsExactly(REVOLUCAO);
        assertThat(habilidades.buscar(filtro(null, "identificar", null, null, null))).containsExactly(TEXTOS, IMAGENS);
    }

    @Test
    void curingasDoLikeSaoTextoComum() {
        habilidades.gravar(List.of(REVOLUCAO, PORCENTAGEM));

        assertThat(habilidades.buscar(filtro(null, "100%", null, null, null))).containsExactly(PORCENTAGEM);
        assertThat(habilidades.buscar(filtro(null, "%", null, null, null))).containsExactly(PORCENTAGEM);
        assertThat(habilidades.buscar(filtro(null, "_", null, null, null))).containsExactly(PORCENTAGEM);
        assertThat(habilidades.buscar(filtro(null, "\\", null, null, null))).isEmpty();
    }

    @Test
    void buscaPorAnoEncontraOsBlocosDeAnos() {
        habilidades.gravar(List.of(REVOLUCAO, IMAGENS, TEXTOS));

        assertThat(habilidades.buscar(filtro(null, null, null, 7, null))).containsExactly(IMAGENS);
        assertThat(habilidades.buscar(filtro(null, null, null, 6, null))).containsExactly(IMAGENS);
        assertThat(habilidades.buscar(filtro(null, null, null, 8, null))).containsExactly(REVOLUCAO);
        assertThat(habilidades.buscar(filtro(null, null, null, 3, null))).containsExactly(TEXTOS);
        assertThat(habilidades.buscar(filtro(null, null, null, 9, null))).isEmpty();
    }

    /** Com as vírgulas em volta, o ano 1 não casa com o 1 de "11" nem com "10". */
    @Test
    void anoNaoCasaComParteDeOutroNumero() {
        jdbc.update("""
                INSERT INTO habilidade_bncc (codigo, descricao, etapa, ano, componente)
                VALUES ('XX11AA01', 'Teste', 'XX', '11', 'Teste'), ('XX10AA01', 'Teste', 'XX', '2,10', 'Teste')""");

        assertThat(habilidades.buscar(filtro(null, null, "XX", 1, null))).isEmpty();
    }

    @Test
    void buscaPorEtapaEComponente() {
        habilidades.gravar(List.of(REVOLUCAO, IMAGENS, TEXTOS));

        assertThat(habilidades.buscar(filtro(null, null, "EF", null, "língua portuguesa"))).containsExactly(TEXTOS, IMAGENS);
        assertThat(habilidades.buscar(filtro(null, null, "EM", null, null))).isEmpty();
        assertThat(habilidades.buscar(filtro(null, null, null, null, "Histó"))).isEmpty();
    }

    @Test
    void filtrosSeCombinam() {
        habilidades.gravar(List.of(REVOLUCAO, IMAGENS, TEXTOS));

        assertThat(habilidades.buscar(filtro(null, "identificar", "EF", 7, "Língua Portuguesa"))).containsExactly(IMAGENS);
        assertThat(habilidades.buscar(filtro("EF08HI03", null, null, 7, null))).isEmpty();
    }

    @Test
    void semFiltroDevolveTudoEmOrdemDeCodigoAteOLimite() {
        habilidades.gravar(List.of(REVOLUCAO, IMAGENS, TEXTOS, PORCENTAGEM));

        assertThat(habilidades.buscar(filtro(null, null, null, null, null)))
                .extracting(HabilidadeBncc::codigo)
                .containsExactly("EF08HI03", "EF09MA05", "EF15LP01", "EF67LP08");
        assertThat(habilidades.buscar(new FiltroDeHabilidades(null, null, null, null, null, 2)))
                .extracting(HabilidadeBncc::codigo)
                .containsExactly("EF08HI03", "EF09MA05");
    }

    @Test
    void existentesDevolveSoOsCodigosDoCatalogo() {
        habilidades.gravar(List.of(REVOLUCAO, IMAGENS));

        assertThat(habilidades.existentes(List.of("EF08HI03", "EF01XX99", "EF67LP08")))
                .containsExactlyInAnyOrder("EF08HI03", "EF67LP08");
    }

    @Test
    void existentesDeListaVaziaNaoConsultaEDevolveVazio() {
        assertThat(habilidades.existentes(List.of())).isEmpty();
    }

    private static FiltroDeHabilidades filtro(
            String codigo, String descricao, String etapa, Integer ano, String componente) {
        return new FiltroDeHabilidades(codigo, descricao, etapa, ano, componente, 50);
    }

    private int contar() {
        return jdbc.queryForObject("SELECT count(*) FROM habilidade_bncc", Integer.class);
    }
}
