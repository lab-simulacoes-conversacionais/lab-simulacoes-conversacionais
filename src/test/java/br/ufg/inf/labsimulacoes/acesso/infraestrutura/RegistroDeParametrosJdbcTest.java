package br.ufg.inf.labsimulacoes.acesso.infraestrutura;

import static br.ufg.inf.labsimulacoes.acesso.infraestrutura.RegistroDeParametrosJdbc.CUSTO_DIARIO_MAXIMO;
import static br.ufg.inf.labsimulacoes.acesso.infraestrutura.RegistroDeParametrosJdbc.LIMITE_DE_PERGUNTAS;
import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.dominio.Parametros;
import br.ufg.inf.labsimulacoes.acesso.dominio.RegistroDeParametros;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Persistência dos parâmetros no PostgreSQL real. Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class RegistroDeParametrosJdbcTest {

    @Autowired
    private RegistroDeParametros registro;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void comecarSemParametros() {
        jdbc.update("DELETE FROM parametro");
    }

    @Test
    void semNadaGravadoValeOPadrao() {
        assertThat(registro.carregar()).isEqualTo(Parametros.padrao());
    }

    @Test
    void parametrosSalvosSaoCarregados() {
        var parametros = new Parametros(25, new BigDecimal("3.75"));

        registro.salvar(parametros);

        assertThat(registro.carregar()).isEqualTo(parametros);
    }

    @Test
    void salvarDeNovoSubstituiOValor() {
        registro.salvar(new Parametros(25, new BigDecimal("3.75")));

        registro.salvar(new Parametros(30, new BigDecimal("10")));

        assertThat(registro.carregar()).isEqualTo(new Parametros(30, new BigDecimal("10")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM parametro", Integer.class)).isEqualTo(2);
    }

    @Test
    void salvarSemCustoApagaOCustoAnterior() {
        registro.salvar(new Parametros(25, new BigDecimal("3.75")));

        registro.salvar(new Parametros(25, null));

        assertThat(registro.carregar().custoDiarioMaximo()).isEmpty();
        assertThat(valoresGravados()).doesNotContainKey(CUSTO_DIARIO_MAXIMO);
    }

    @Test
    void soOCustoGravadoMantemOLimitePadrao() {
        jdbc.update("INSERT INTO parametro (chave, valor) VALUES (?, '5')", CUSTO_DIARIO_MAXIMO);

        assertThat(registro.carregar()).isEqualTo(new Parametros(40, new BigDecimal("5")));
    }

    /** O valor fica legível para quem consulta o banco direto. */
    @Test
    void valoresSaoGravadosComoTextoSimples() {
        registro.salvar(new Parametros(40, new BigDecimal("50.00")));

        assertThat(valoresGravados())
                .containsEntry(LIMITE_DE_PERGUNTAS, "40")
                .containsEntry(CUSTO_DIARIO_MAXIMO, "50");
    }

    @Test
    void salvarAtualizaADataDeAlteracao() {
        registro.salvar(Parametros.padrao());
        jdbc.update("UPDATE parametro SET atualizado_em = '2026-01-01'");

        registro.salvar(new Parametros(30, null));

        assertThat(jdbc.queryForObject(
                "SELECT atualizado_em > '2026-01-01' FROM parametro WHERE chave = ?", Boolean.class,
                LIMITE_DE_PERGUNTAS))
                .isTrue();
    }

    private Map<String, String> valoresGravados() {
        var valores = new HashMap<String, String>();
        jdbc.query("SELECT chave, valor FROM parametro",
                linha -> {
                    valores.put(linha.getString("chave"), linha.getString("valor"));
                });
        return valores;
    }
}
