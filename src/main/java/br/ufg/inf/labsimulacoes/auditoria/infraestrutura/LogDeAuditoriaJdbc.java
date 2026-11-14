package br.ufg.inf.labsimulacoes.auditoria.infraestrutura;

import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.FiltroDoLog;
import br.ufg.inf.labsimulacoes.auditoria.dominio.LogDeAuditoria;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Log de auditoria na tabela log_auditoria, que tem trigger contra UPDATE, DELETE e TRUNCATE. */
@Repository
class LogDeAuditoriaJdbc implements LogDeAuditoria {

    private static final TypeReference<Map<String, Object>> DETALHES = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate comNomes;
    private final ObjectMapper json;

    LogDeAuditoriaJdbc(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.comNomes = new NamedParameterJdbcTemplate(jdbc);
        this.json = json;
    }

    /**
     * O modo SHARE ROW EXCLUSIVE conflita com ele mesmo e com o INSERT, mas não com a leitura. Assim,
     * a consulta do log continua livre, e até um INSERT feito fora do sistema espera a vez. O
     * PostgreSQL recusa o LOCK fora de uma transação, o que impede o uso errado.
     */
    @Override
    public Optional<String> travarEBuscarUltimoHash() {
        jdbc.execute("LOCK TABLE log_auditoria IN SHARE ROW EXCLUSIVE MODE");
        return jdbc.query(
                        "SELECT hash FROM log_auditoria ORDER BY id DESC LIMIT 1",
                        (linha, numero) -> Optional.ofNullable(linha.getString("hash")))
                .stream()
                .findFirst()
                .flatMap(hash -> hash);
    }

    @Override
    public RegistroDeAuditoria gravar(RegistroDeAuditoria registro) {
        Long id = jdbc.queryForObject("""
                INSERT INTO log_auditoria (usuario_id, acao, entidade, entidade_id, detalhes, hash, ocorrido_em)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                RETURNING id""",
                Long.class,
                registro.usuarioId(),
                registro.acao().name(),
                registro.entidade(),
                registro.entidadeId(),
                paraJson(registro.detalhes()),
                registro.hash(),
                Timestamp.from(registro.ocorridoEm()));
        return new RegistroDeAuditoria(id, registro.usuarioId(), registro.acao(), registro.entidadeId(),
                registro.detalhes(), registro.ocorridoEm(), registro.hash());
    }

    @Override
    public List<RegistroDeAuditoria> listarEmOrdem() {
        return jdbc.query("""
                SELECT id, usuario_id, acao, entidade_id, detalhes::text AS detalhes, hash, ocorrido_em
                FROM log_auditoria ORDER BY id""",
                this::paraRegistro);
    }

    /**
     * O texto da consulta é fixo: um filtro ausente vai como parâmetro nulo e deixa de restringir.
     * Nas pontas do período, o nulo vira infinito dentro do próprio SQL, o que mantém o uso do índice
     * de ocorrido_em.
     */
    private static final String CONSULTA = """
            SELECT id, usuario_id, acao, entidade_id, detalhes::text AS detalhes, hash, ocorrido_em
            FROM log_auditoria
            WHERE ocorrido_em >= COALESCE(CAST(:inicio AS TIMESTAMPTZ), '-infinity')
              AND ocorrido_em < COALESCE(CAST(:fim AS TIMESTAMPTZ), 'infinity')
              AND (CAST(:usuarioId AS BIGINT) IS NULL OR usuario_id = :usuarioId)
              AND (CAST(:acao AS TEXT) IS NULL OR acao = :acao)
            ORDER BY ocorrido_em DESC, id DESC
            LIMIT :tamanho OFFSET :deslocamento""";

    @Override
    public List<RegistroDeAuditoria> consultar(FiltroDoLog filtro) {
        var parametros = new MapSqlParameterSource()
                .addValue("inicio", paraTimestamp(filtro.inicio()), Types.TIMESTAMP)
                .addValue("fim", paraTimestamp(filtro.fim()), Types.TIMESTAMP)
                .addValue("usuarioId", filtro.usuarioId(), Types.BIGINT)
                .addValue("acao", filtro.acao() == null ? null : filtro.acao().name(), Types.VARCHAR)
                .addValue("tamanho", filtro.tamanho())
                .addValue("deslocamento", filtro.deslocamento());
        return comNomes.query(CONSULTA, parametros, this::paraRegistro);
    }

    private static Timestamp paraTimestamp(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }

    private RegistroDeAuditoria paraRegistro(ResultSet linha, int numero) throws SQLException {
        return new RegistroDeAuditoria(
                linha.getLong("id"),
                linha.getLong("usuario_id"),
                AcaoCritica.valueOf(linha.getString("acao")),
                linha.getString("entidade_id"),
                deJson(linha.getString("detalhes")),
                linha.getTimestamp("ocorrido_em").toInstant(),
                linha.getString("hash"));
    }

    private String paraJson(Map<String, Object> detalhes) {
        try {
            return json.writeValueAsString(detalhes);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Detalhes do log que não viram JSON", e);
        }
    }

    private Map<String, Object> deJson(String detalhes) {
        try {
            return json.readValue(detalhes, DETALHES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Detalhes do log com JSON inválido", e);
        }
    }
}
