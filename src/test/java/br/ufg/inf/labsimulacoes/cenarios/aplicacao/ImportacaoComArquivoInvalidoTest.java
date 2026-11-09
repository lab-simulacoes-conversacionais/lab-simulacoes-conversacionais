package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CatalogoInvalido;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Importação de ponta a ponta com um arquivo em que a segunda habilidade tem um componente que a
 * estrutura não conhece. A primeira é válida, e mesmo assim nada pode ser gravado.
 */
@SpringBootTest(properties = {
    "bncc.arquivos.ensino-fundamental=classpath:bncc-fixtures/componente-desconhecido.json",
    "bncc.arquivos.estrutura=classpath:bncc-fixtures/estrutura.json"
})
@Import(InfraestruturaDeTeste.class)
class ImportacaoComArquivoInvalidoTest {

    @Autowired
    private ImportacaoDoCatalogo importacao;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void componenteDesconhecidoFalhaSemGravarNada() {
        int antes = jdbc.queryForObject("SELECT count(*) FROM habilidade_bncc", Integer.class);

        assertThatThrownBy(importacao::importar)
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("ef-comp-xx");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM habilidade_bncc", Integer.class)).isEqualTo(antes);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM habilidade_bncc WHERE codigo = 'EF08HI03'", Integer.class)).isZero();
    }
}
