package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Com a propriedade ligada, como fora dos testes, o catálogo já está no banco quando a aplicação sobe. */
@SpringBootTest(properties = "bncc.importar-ao-iniciar=true")
@Import(InfraestruturaDeTeste.class)
class ImportacaoNaSubidaLigadaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void subidaImportaOCatalogo() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM habilidade_bncc", Integer.class)).isEqualTo(1304);
        assertThat(jdbc.queryForObject(
                "SELECT componente FROM habilidade_bncc WHERE codigo = 'EF08HI03'", String.class)).isEqualTo("História");
    }
}
