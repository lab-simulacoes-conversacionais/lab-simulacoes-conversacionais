package br.ufg.inf.labsimulacoes.sessoes.infraestrutura;

import br.ufg.inf.labsimulacoes.sessoes.MensagemDaRevisao;
import br.ufg.inf.labsimulacoes.sessoes.TrechoParaSinalizar;
import br.ufg.inf.labsimulacoes.sessoes.dominio.ConversasGravadas;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** As mensagens de mensagem, com a sessão de sessao, na ordem em que foram gravadas. */
@Repository
class ConversasGravadasJdbc implements ConversasGravadas {

    private static final String SESSOES_DO_PROFESSOR = """
            SELECT id FROM sessao
            WHERE professor_id = ? AND tipo = 'SIMULACAO' AND COALESCE(encerrada_em, iniciada_em) >= ?
            ORDER BY iniciada_em DESC, id DESC""";

    private static final String MENSAGENS = """
            SELECT id, autoria, texto, criado_em, resposta_bloqueada FROM mensagem
            WHERE sessao_id = ?
            ORDER BY criado_em, id""";

    /**
     * A pergunta é a mensagem da turma logo antes da resposta, na mesma ordem da conversa. Cada pergunta
     * tem no máximo uma resposta, e uma pergunta com a resposta bloqueada não tem nenhuma, então a última
     * pergunta antes da resposta é sempre a que a originou, mesmo depois de um reinício.
     */
    private static final String RESPOSTA = """
            SELECT ia.id, ia.sessao_id, ia.texto, ia.criado_em, ia.resposta_ia_meta_id,
                   s.professor_id, s.cenario_versao_id, s.iniciada_em, s.encerrada_em, pergunta.texto AS pergunta
            FROM mensagem ia
            JOIN sessao s ON s.id = ia.sessao_id
            JOIN LATERAL (
                SELECT turma.texto FROM mensagem turma
                WHERE turma.sessao_id = ia.sessao_id
                  AND turma.autoria = 'TURMA'
                  AND (turma.criado_em, turma.id) < (ia.criado_em, ia.id)
                ORDER BY turma.criado_em DESC, turma.id DESC
                LIMIT 1) pergunta ON true
            WHERE ia.id = ? AND ia.autoria = 'IA'""";

    private final JdbcTemplate jdbc;

    ConversasGravadasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Long> sessoesDoProfessorDesde(long professorId, Instant desde) {
        return jdbc.queryForList(SESSOES_DO_PROFESSOR, Long.class, professorId, desde.atOffset(ZoneOffset.UTC));
    }

    @Override
    public List<MensagemDaRevisao> mensagens(long sessaoId) {
        return jdbc.query(MENSAGENS, ConversasGravadasJdbc::paraMensagem, sessaoId);
    }

    @Override
    public Optional<TrechoParaSinalizar> resposta(long mensagemId) {
        return jdbc.query(RESPOSTA, ConversasGravadasJdbc::paraTrecho, mensagemId).stream().findFirst();
    }

    private static MensagemDaRevisao paraMensagem(ResultSet linha, int numero) throws SQLException {
        return new MensagemDaRevisao(
                linha.getLong("id"),
                "TURMA".equals(linha.getString("autoria")),
                linha.getString("texto"),
                instante(linha, "criado_em"),
                linha.getBoolean("resposta_bloqueada"));
    }

    private static TrechoParaSinalizar paraTrecho(ResultSet linha, int numero) throws SQLException {
        return new TrechoParaSinalizar(
                linha.getLong("sessao_id"),
                linha.getLong("professor_id"),
                linha.getLong("cenario_versao_id"),
                instante(linha, "iniciada_em"),
                instante(linha, "encerrada_em"),
                linha.getLong("id"),
                linha.getString("pergunta"),
                linha.getString("texto"),
                instante(linha, "criado_em"),
                linha.getObject("resposta_ia_meta_id", Long.class));
    }

    private static Instant instante(ResultSet linha, String coluna) throws SQLException {
        Timestamp momento = linha.getTimestamp(coluna);
        return momento == null ? null : momento.toInstant();
    }
}
