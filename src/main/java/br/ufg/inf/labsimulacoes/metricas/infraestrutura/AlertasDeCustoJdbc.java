package br.ufg.inf.labsimulacoes.metricas.infraestrutura;

import br.ufg.inf.labsimulacoes.metricas.dominio.AlertaDeCusto;
import br.ufg.inf.labsimulacoes.metricas.dominio.AlertasDeCusto;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tabela alerta_custo_diario, com o dia como chave primária. O ON CONFLICT faz o banco decidir qual
 * conferência grava o alerta do dia, sem trava na aplicação.
 */
@Repository
class AlertasDeCustoJdbc implements AlertasDeCusto {

    private final JdbcTemplate jdbc;

    AlertasDeCustoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean registrar(AlertaDeCusto alerta) {
        return jdbc.update(
                "INSERT INTO alerta_custo_diario (dia, custo, limite) VALUES (?, ?, ?) ON CONFLICT (dia) DO NOTHING",
                alerta.dia(), alerta.custo(), alerta.limite()) == 1;
    }

    @Override
    public Optional<AlertaDeCusto> doDia(LocalDate dia) {
        return jdbc.query("SELECT dia, custo, limite FROM alerta_custo_diario WHERE dia = ?",
                        (linha, numero) -> new AlertaDeCusto(
                                linha.getObject("dia", LocalDate.class),
                                linha.getBigDecimal("custo"),
                                linha.getBigDecimal("limite")),
                        dia)
                .stream()
                .findFirst();
    }
}
