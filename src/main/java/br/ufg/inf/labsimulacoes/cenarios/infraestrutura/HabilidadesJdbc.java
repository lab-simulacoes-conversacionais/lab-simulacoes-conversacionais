package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.FiltroDeHabilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ResultadoDaGravacao;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tabela habilidade_bncc com SQL direto: o upsert e a busca por ano dentro da lista de anos são mais
 * simples em SQL do que em JPA.
 */
@Repository
class HabilidadesJdbc implements Habilidades {

    /**
     * Um comando só para o catálogo inteiro, então ou tudo é gravado ou nada é. Linha que já está
     * igual não é tocada (o WHERE do DO UPDATE), e o criado_em nunca muda. Nenhuma linha é apagada,
     * porque versao_habilidade aponta para habilidade_bncc. O xmax = 0 só vale para linha inserida.
     */
    private static final String UPSERT = """
            INSERT INTO habilidade_bncc (codigo, descricao, etapa, ano, componente)
            SELECT * FROM unnest(?::text[], ?::text[], ?::text[], ?::text[], ?::text[])
            ON CONFLICT (codigo) DO UPDATE SET
                descricao = excluded.descricao,
                etapa = excluded.etapa,
                ano = excluded.ano,
                componente = excluded.componente
            WHERE (habilidade_bncc.descricao, habilidade_bncc.etapa, habilidade_bncc.ano, habilidade_bncc.componente)
                IS DISTINCT FROM (excluded.descricao, excluded.etapa, excluded.ano, excluded.componente)
            RETURNING (xmax = 0) AS inserida""";

    private final JdbcTemplate jdbc;

    HabilidadesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ResultadoDaGravacao gravar(List<HabilidadeBncc> habilidades) {
        if (habilidades.isEmpty()) {
            return new ResultadoDaGravacao(0, 0);
        }
        int[] contagem = new int[2];
        jdbc.query(
                conexao -> {
                    PreparedStatement comando = conexao.prepareStatement(UPSERT);
                    comando.setArray(1, conexao.createArrayOf("text", coluna(habilidades, HabilidadeBncc::codigo)));
                    comando.setArray(2, conexao.createArrayOf("text", coluna(habilidades, HabilidadeBncc::descricao)));
                    comando.setArray(3, conexao.createArrayOf("text", coluna(habilidades, HabilidadeBncc::etapa)));
                    comando.setArray(4, conexao.createArrayOf("text", coluna(habilidades, HabilidadeBncc::anosComoTexto)));
                    comando.setArray(5, conexao.createArrayOf("text", coluna(habilidades, HabilidadeBncc::componente)));
                    return comando;
                },
                linha -> {
                    contagem[linha.getBoolean("inserida") ? 0 : 1]++;
                });
        return new ResultadoDaGravacao(contagem[0], contagem[1]);
    }

    /**
     * Cada filtro preenchido acrescenta uma condição. Descrição e componente passam pelo lower() do
     * próprio banco dos dois lados, para que a comparação sem maiúsculas use a mesma regra.
     */
    @Override
    public List<HabilidadeBncc> buscar(FiltroDeHabilidades filtro) {
        var sql = new StringBuilder("SELECT codigo, descricao, etapa, ano, componente FROM habilidade_bncc WHERE true");
        var parametros = new ArrayList<Object>();
        if (filtro.codigo() != null) {
            sql.append(" AND codigo = ?");
            parametros.add(filtro.codigo());
        }
        if (filtro.descricao() != null) {
            sql.append(" AND lower(descricao) LIKE lower(?) ESCAPE '\\'");
            parametros.add("%" + semCuringas(filtro.descricao()) + "%");
        }
        if (filtro.etapa() != null) {
            sql.append(" AND etapa = ?");
            parametros.add(filtro.etapa());
        }
        if (filtro.ano() != null) {
            // Com as vírgulas em volta, o ano 1 não casa com o "10" de outra lista nem com o "1" de "11".
            sql.append(" AND position(? IN ',' || ano || ',') > 0");
            parametros.add("," + filtro.ano() + ",");
        }
        if (filtro.componente() != null) {
            sql.append(" AND lower(componente) = lower(?)");
            parametros.add(filtro.componente());
        }
        sql.append(" ORDER BY codigo LIMIT ?");
        parametros.add(filtro.limite());
        return jdbc.query(sql.toString(), (linha, numero) -> habilidade(linha), parametros.toArray());
    }

    private static HabilidadeBncc habilidade(ResultSet linha) throws SQLException {
        List<Integer> anos = Arrays.stream(linha.getString("ano").split(",")).map(Integer::valueOf).toList();
        return new HabilidadeBncc(
                linha.getString("codigo"),
                linha.getString("descricao"),
                linha.getString("etapa"),
                anos,
                linha.getString("componente"));
    }

    /** Sem isto, um "%" ou "_" digitado na busca valeria como curinga do LIKE. */
    private static String semCuringas(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Object[] coluna(List<HabilidadeBncc> habilidades, Function<HabilidadeBncc, String> campo) {
        return habilidades.stream().map(campo).toArray();
    }
}
