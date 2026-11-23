package br.ufg.inf.labsimulacoes.auditoria.infraestrutura;

import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.CursorDoLog;
import br.ufg.inf.labsimulacoes.auditoria.dominio.FiltroDoLog;
import br.ufg.inf.labsimulacoes.auditoria.dominio.LogDeAuditoria;
import br.ufg.inf.labsimulacoes.auditoria.dominio.PaginaDoLog;
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
     * Os textos da consulta são fixos: um filtro ausente vai como parâmetro nulo e deixa de restringir.
     * Nas pontas do período, o nulo vira infinito dentro do próprio SQL, o que mantém o uso do índice
     * de ocorrido_em. O usuário é a exceção: com um "IS NULL OR" no lugar dele, o plano genérico que o
     * driver passa a usar depois de algumas execuções não sabe se o filtro veio e deixa de usar o
     * índice de usuario_id. Por isso há um texto com o usuário e outro sem. A ação tem poucos valores
     * e dispensa índice.
     *
     * <p>A página continua depois do cursor, na mesma ordem do ORDER BY, em vez de pular linhas: uma
     * ação gravada entre uma página e outra não repete nem esconde linhas. Sem cursor, a comparação
     * com infinito deixa passar todas as linhas. A consulta pede uma linha a mais que o tamanho da
     * página só para saber se existe a próxima.
     */
    private static final String CONSULTA_SEM_USUARIO = """
            SELECT id, usuario_id, acao, entidade_id, detalhes::text AS detalhes, hash, ocorrido_em
            FROM log_auditoria
            WHERE ocorrido_em >= COALESCE(CAST(:inicio AS TIMESTAMPTZ), '-infinity')
              AND ocorrido_em < COALESCE(CAST(:fim AS TIMESTAMPTZ), 'infinity')
              AND (CAST(:acao AS TEXT) IS NULL OR acao = :acao)
              AND (ocorrido_em, id) < (COALESCE(CAST(:cursorOcorridoEm AS TIMESTAMPTZ), 'infinity'),
                                       COALESCE(CAST(:cursorId AS BIGINT), 9223372036854775807))
            ORDER BY ocorrido_em DESC, id DESC
            LIMIT :limite""";

    private static final String CONSULTA_DO_USUARIO = """
            SELECT id, usuario_id, acao, entidade_id, detalhes::text AS detalhes, hash, ocorrido_em
            FROM log_auditoria
            WHERE usuario_id = :usuarioId
              AND ocorrido_em >= COALESCE(CAST(:inicio AS TIMESTAMPTZ), '-infinity')
              AND ocorrido_em < COALESCE(CAST(:fim AS TIMESTAMPTZ), 'infinity')
              AND (CAST(:acao AS TEXT) IS NULL OR acao = :acao)
              AND (ocorrido_em, id) < (COALESCE(CAST(:cursorOcorridoEm AS TIMESTAMPTZ), 'infinity'),
                                       COALESCE(CAST(:cursorId AS BIGINT), 9223372036854775807))
            ORDER BY ocorrido_em DESC, id DESC
            LIMIT :limite""";

    @Override
    public PaginaDoLog consultar(FiltroDoLog filtro) {
        CursorDoLog cursor = filtro.cursor();
        Instant cursorOcorridoEm = cursor == null ? null : cursor.ocorridoEm();
        Long cursorId = cursor == null ? null : cursor.id();
        var parametros = new MapSqlParameterSource()
                .addValue("inicio", paraTimestamp(filtro.inicio()), Types.TIMESTAMP)
                .addValue("fim", paraTimestamp(filtro.fim()), Types.TIMESTAMP)
                .addValue("usuarioId", filtro.usuarioId(), Types.BIGINT)
                .addValue("acao", filtro.acao() == null ? null : filtro.acao().name(), Types.VARCHAR)
                .addValue("cursorOcorridoEm", paraTimestamp(cursorOcorridoEm), Types.TIMESTAMP)
                .addValue("cursorId", cursorId, Types.BIGINT)
                .addValue("limite", filtro.tamanho() + 1);
        String consulta = filtro.usuarioId() == null ? CONSULTA_SEM_USUARIO : CONSULTA_DO_USUARIO;
        return PaginaDoLog.lidaComUmaAMais(
                comNomes.query(consulta, parametros, this::paraRegistro), filtro.tamanho());
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
