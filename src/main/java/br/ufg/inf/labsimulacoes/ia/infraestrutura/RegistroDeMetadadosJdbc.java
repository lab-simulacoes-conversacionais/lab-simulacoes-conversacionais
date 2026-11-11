package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import br.ufg.inf.labsimulacoes.ia.dominio.MetadadosDaResposta;
import br.ufg.inf.labsimulacoes.ia.dominio.RegistroDeMetadados;
import br.ufg.inf.labsimulacoes.ia.dominio.UsoDoModelo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Metadados das respostas na tabela resposta_ia_meta. */
@Repository
class RegistroDeMetadadosJdbc implements RegistroDeMetadados {

    private static final TypeReference<Map<String, Object>> PARAMETROS = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    RegistroDeMetadadosJdbc(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public MetadadosDaResposta gravar(MetadadosDaResposta metadados) {
        Long id = jdbc.queryForObject("""
                INSERT INTO resposta_ia_meta
                    (provedor, modelo, parametros, versao_prompt, tokens_entrada, tokens_saida, custo, latencia_ms)
                VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                RETURNING id""",
                Long.class,
                metadados.provedor(),
                metadados.modelo(),
                paraJson(metadados.parametros()),
                metadados.versaoPrompt(),
                metadados.uso().tokensEntrada(),
                metadados.uso().tokensSaida(),
                metadados.custo(),
                metadados.latenciaMs());
        return metadados.comId(id);
    }

    @Override
    public Optional<MetadadosDaResposta> buscar(long id) {
        return jdbc.query("""
                        SELECT id, provedor, modelo, parametros::text AS parametros, versao_prompt,
                               tokens_entrada, tokens_saida, custo, latencia_ms
                        FROM resposta_ia_meta WHERE id = ?""",
                        this::paraMetadados,
                        id)
                .stream()
                .findFirst();
    }

    private MetadadosDaResposta paraMetadados(ResultSet linha, int numero) throws SQLException {
        return new MetadadosDaResposta(
                linha.getLong("id"),
                linha.getString("provedor"),
                linha.getString("modelo"),
                deJson(linha.getString("parametros")),
                linha.getString("versao_prompt"),
                new UsoDoModelo(
                        linha.getObject("tokens_entrada", Integer.class),
                        linha.getObject("tokens_saida", Integer.class)),
                linha.getBigDecimal("custo"),
                linha.getInt("latencia_ms"));
    }

    private String paraJson(Map<String, Object> parametros) {
        try {
            return json.writeValueAsString(parametros);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Parâmetros do modelo que não viram JSON", e);
        }
    }

    private Map<String, Object> deJson(String parametros) {
        try {
            return json.readValue(parametros, PARAMETROS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Parâmetros do modelo com JSON inválido", e);
        }
    }
}
