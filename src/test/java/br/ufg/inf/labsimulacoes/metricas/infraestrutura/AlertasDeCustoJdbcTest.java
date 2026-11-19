package br.ufg.inf.labsimulacoes.metricas.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.metricas.dominio.AlertaDeCusto;
import br.ufg.inf.labsimulacoes.metricas.dominio.AlertasDeCusto;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Alertas de custo diário no PostgreSQL real (RNF-13). Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class AlertasDeCustoJdbcTest {

    private static final LocalDate DIA = LocalDate.of(2033, 1, 15);

    @Autowired
    private AlertasDeCusto alertas;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void primeiroAlertaDoDiaEGravadoELidoIgual() {
        var alerta = new AlertaDeCusto(DIA, new BigDecimal("0.020000"), new BigDecimal("0.01"));

        assertThat(alertas.registrar(alerta)).isTrue();

        AlertaDeCusto lido = alertas.doDia(DIA).orElseThrow();
        assertThat(lido.dia()).isEqualTo(DIA);
        assertThat(lido.custo()).isEqualByComparingTo("0.02");
        assertThat(lido.limite()).isEqualByComparingTo("0.01");
    }

    /** O banco decide: o segundo alerta do mesmo dia não é gravado e o primeiro fica como estava. */
    @Test
    void segundoAlertaDoMesmoDiaNaoEGravado() {
        alertas.registrar(new AlertaDeCusto(DIA, new BigDecimal("0.02"), new BigDecimal("0.01")));

        boolean gravou = alertas.registrar(new AlertaDeCusto(DIA, new BigDecimal("9"), new BigDecimal("5")));

        assertThat(gravou).isFalse();
        assertThat(alertas.doDia(DIA).orElseThrow().custo()).isEqualByComparingTo("0.02");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alerta_custo_diario WHERE dia = ?", Integer.class, DIA))
                .isEqualTo(1);
    }

    @Test
    void cadaDiaTemOSeuAlerta() {
        alertas.registrar(new AlertaDeCusto(DIA, new BigDecimal("0.02"), new BigDecimal("0.01")));

        assertThat(alertas.registrar(new AlertaDeCusto(DIA.plusDays(1), new BigDecimal("0.02"), new BigDecimal("0.01"))))
                .isTrue();
        assertThat(alertas.doDia(DIA.plusDays(2))).isEmpty();
    }

    /** Um limite maior que qualquer custo de uma resposta cabe, porque o parâmetro não tem teto. */
    @Test
    void limiteGrandeCabe() {
        assertThat(alertas.registrar(new AlertaDeCusto(DIA, new BigDecimal("123456789.5"), new BigDecimal("100000000"))))
                .isTrue();
        assertThat(alertas.doDia(DIA).orElseThrow().limite()).isEqualByComparingTo("100000000");
    }
}
