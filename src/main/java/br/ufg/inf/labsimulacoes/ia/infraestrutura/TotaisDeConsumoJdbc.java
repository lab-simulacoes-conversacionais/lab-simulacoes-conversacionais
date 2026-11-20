package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import br.ufg.inf.labsimulacoes.ia.ResumoDoConsumo;
import br.ufg.inf.labsimulacoes.ia.dominio.TotaisDeConsumo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Soma os metadados de resposta_ia_meta, pelos índices de criado_em e de (finalidade, referencia_id).
 * O custo nulo, de uma resposta sem tokens informados, não entra na soma e é contado à parte.
 */
@Repository
class TotaisDeConsumoJdbc implements TotaisDeConsumo {

    private static final String TOTAIS = """
            SELECT count(*) AS respostas,
                   coalesce(sum(tokens_entrada), 0) AS tokens_entrada,
                   coalesce(sum(tokens_saida), 0) AS tokens_saida,
                   coalesce(sum(custo), 0) AS custo,
                   count(*) FILTER (WHERE custo IS NULL) AS sem_custo
            FROM resposta_ia_meta
            """;

    private final JdbcTemplate jdbc;

    TotaisDeConsumoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ResumoDoConsumo entre(Instant inicio, Instant fim) {
        return jdbc.queryForObject(TOTAIS + "WHERE criado_em >= ? AND criado_em < ?",
                (linha, numero) -> paraResumo(linha), utc(inicio), utc(fim));
    }

    @Override
    public ResumoDoConsumo daReferencia(FinalidadeDoConsumo finalidade, long referenciaId) {
        return jdbc.queryForObject(TOTAIS + "WHERE finalidade = ? AND referencia_id = ?",
                (linha, numero) -> paraResumo(linha), finalidade.name(), referenciaId);
    }

    private static ResumoDoConsumo paraResumo(ResultSet linha) throws SQLException {
        return new ResumoDoConsumo(
                linha.getLong("respostas"),
                linha.getLong("tokens_entrada"),
                linha.getLong("tokens_saida"),
                linha.getBigDecimal("custo"),
                linha.getLong("sem_custo"));
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
