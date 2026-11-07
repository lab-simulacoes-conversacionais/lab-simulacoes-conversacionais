package br.ufg.inf.labsimulacoes.acesso.infraestrutura;

import br.ufg.inf.labsimulacoes.acesso.dominio.Parametros;
import br.ufg.inf.labsimulacoes.acesso.dominio.RegistroDeParametros;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Guarda cada parâmetro em uma linha da tabela parametro, com o valor em texto. A tabela é de chave
 * e valor, então o JDBC direto fica mais simples que uma entidade JPA.
 *
 * <p>O valor padrão fica no domínio, e não numa linha gravada pela migration, para que o padrão
 * esteja num lugar só.
 */
@Repository
class RegistroDeParametrosJdbc implements RegistroDeParametros {

    static final String LIMITE_DE_PERGUNTAS = "limite_perguntas_por_sessao";
    static final String CUSTO_DIARIO_MAXIMO = "custo_diario_maximo_usd";

    private final JdbcTemplate jdbc;

    RegistroDeParametrosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Parametros carregar() {
        Map<String, String> valores = new HashMap<>();
        jdbc.query("SELECT chave, valor FROM parametro WHERE chave IN (?, ?)",
                linha -> {
                    valores.put(linha.getString("chave"), linha.getString("valor"));
                },
                LIMITE_DE_PERGUNTAS, CUSTO_DIARIO_MAXIMO);

        String limite = valores.get(LIMITE_DE_PERGUNTAS);
        String custo = valores.get(CUSTO_DIARIO_MAXIMO);
        return new Parametros(
                limite == null ? Parametros.LIMITE_DE_PERGUNTAS_PADRAO : Integer.parseInt(limite),
                custo == null ? null : new BigDecimal(custo));
    }

    @Override
    public void salvar(Parametros parametros) {
        gravar(LIMITE_DE_PERGUNTAS, String.valueOf(parametros.limiteDePerguntasPorSessao()));
        parametros.custoDiarioMaximo().ifPresentOrElse(
                custo -> gravar(CUSTO_DIARIO_MAXIMO, custo.toPlainString()),
                () -> jdbc.update("DELETE FROM parametro WHERE chave = ?", CUSTO_DIARIO_MAXIMO));
    }

    private void gravar(String chave, String valor) {
        jdbc.update("""
                INSERT INTO parametro (chave, valor) VALUES (?, ?)
                ON CONFLICT (chave) DO UPDATE SET valor = excluded.valor, atualizado_em = now()""",
                chave, valor);
    }
}
