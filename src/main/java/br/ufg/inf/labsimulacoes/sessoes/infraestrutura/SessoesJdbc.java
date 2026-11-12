package br.ufg.inf.labsimulacoes.sessoes.infraestrutura;

import br.ufg.inf.labsimulacoes.sessoes.dominio.EventoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.HistoricoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.TurmaComSessaoEmAndamento;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tabelas sessao, evento_sessao e mensagem com SQL direto, como em Cenários: a reconstrução do estado
 * soma eventos e corta mensagens pelo último reinício, consultas que em JPA virariam SQL de qualquer
 * jeito.
 *
 * <p>O corte compara mensagem.criado_em com o horário do reinício, que vem da aplicação. Por isso as
 * mensagens também precisam ser gravadas com o horário da aplicação, e não com o {@code now()} do
 * banco, que é o início da transação.
 */
@Repository
class SessoesJdbc implements Sessoes {

    /** Índice do RN-10 criado no esquema base. */
    private static final String INDICE_DO_RN_10 = "uq_sessao_em_andamento_por_turma";

    private static final String COLUNAS = """
            id, professor_id, turma_id, cenario_versao_id, limite_perguntas, status, motivo_pausa, iniciada_em,
            encerrada_em""";

    private static final String BUSCAR = "SELECT " + COLUNAS + " FROM sessao WHERE id = ?";

    /** As mais recentes depois do último reinício, devolvidas da mais antiga para a mais nova. */
    private static final String MENSAGENS_DESDE_O_REINICIO = """
            SELECT autoria, texto, criado_em, resposta_bloqueada FROM (
                SELECT id, autoria, texto, criado_em, resposta_bloqueada FROM mensagem
                WHERE sessao_id = ?
                  AND criado_em > COALESCE(
                      (SELECT max(ocorrido_em) FROM evento_sessao WHERE sessao_id = ? AND tipo = 'REINICIO'),
                      '-infinity')
                ORDER BY criado_em DESC, id DESC
                LIMIT ?) recentes
            ORDER BY criado_em, id""";

    private final JdbcTemplate jdbc;

    SessoesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Sessao> buscar(long id) {
        return jdbc.query(BUSCAR, SessoesJdbc::paraSessao, id).stream().findFirst();
    }

    @Override
    public Optional<Sessao> buscarParaAlterar(long id) {
        return jdbc.query(BUSCAR + " FOR UPDATE", SessoesJdbc::paraSessao, id).stream().findFirst();
    }

    @Override
    public boolean temSessaoEmAndamento(long turmaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM sessao WHERE turma_id = ? AND status = 'EM_ANDAMENTO')",
                Boolean.class,
                turmaId));
    }

    @Override
    public Sessao salvar(Sessao sessao) {
        try {
            return sessao.id() == null ? inserir(sessao) : atualizar(sessao);
        } catch (DuplicateKeyException e) {
            if (String.valueOf(e.getMessage()).contains(INDICE_DO_RN_10)) {
                throw new TurmaComSessaoEmAndamento(sessao.turmaId());
            }
            throw e;
        }
    }

    @Override
    public void registrar(long sessaoId, EventoDaSessao evento) {
        jdbc.update(
                "INSERT INTO evento_sessao (sessao_id, tipo, motivo, ocorrido_em) VALUES (?, ?, ?, ?)",
                sessaoId,
                evento.tipo().name(),
                evento.motivo() == null ? null : evento.motivo().name(),
                utc(evento.em()));
    }

    @Override
    public void registrarMensagem(long sessaoId, MensagemExibida mensagem, Long respostaIaMetaId) {
        if ((mensagem.autoria() == MensagemExibida.Autoria.IA) != (respostaIaMetaId != null)) {
            throw new IllegalArgumentException("Só a resposta da IA tem metadados, e ela sempre tem (RNF-10)");
        }
        jdbc.update(
                "INSERT INTO mensagem (sessao_id, autoria, texto, resposta_ia_meta_id, criado_em) VALUES (?, ?, ?, ?, ?)",
                sessaoId,
                mensagem.autoria().name(),
                mensagem.texto(),
                respostaIaMetaId,
                utc(mensagem.em()));
    }

    @Override
    public boolean marcarRespostaBloqueada(long sessaoId) {
        return jdbc.update(
                        """
                        UPDATE mensagem SET resposta_bloqueada = true
                        WHERE id = (SELECT max(id) FROM mensagem WHERE sessao_id = ?) AND autoria = 'TURMA'""",
                        sessaoId)
                == 1;
    }

    @Override
    public Optional<HistoricoDaSessao> historico(long sessaoId, int mensagens) {
        return buscar(sessaoId).map(sessao -> new HistoricoDaSessao(
                sessao,
                jdbc.query(
                        "SELECT tipo, motivo, ocorrido_em FROM evento_sessao WHERE sessao_id = ? ORDER BY ocorrido_em, id",
                        (linha, numero) -> paraEvento(linha),
                        sessaoId),
                contarPerguntas(sessaoId),
                jdbc.query(
                        MENSAGENS_DESDE_O_REINICIO,
                        (linha, numero) -> paraMensagem(linha),
                        sessaoId,
                        sessaoId,
                        mensagens)));
    }

    private Sessao inserir(Sessao sessao) {
        Long id = jdbc.queryForObject(
                """
                INSERT INTO sessao (professor_id, turma_id, cenario_versao_id, limite_perguntas, status, motivo_pausa,
                                    iniciada_em)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""",
                Long.class,
                sessao.professorId(),
                sessao.turmaId(),
                sessao.versaoId(),
                sessao.limiteDePerguntas(),
                sessao.status().name(),
                nome(sessao.motivoDaPausa()),
                utc(sessao.iniciadaEm()));
        return sessao.comId(id);
    }

    /** Professor, turma, versão, limite e início nunca mudam (RN-10, RN-18); só o estado da sessão muda. */
    private Sessao atualizar(Sessao sessao) {
        jdbc.update(
                "UPDATE sessao SET status = ?, motivo_pausa = ?, encerrada_em = ? WHERE id = ?",
                sessao.status().name(),
                nome(sessao.motivoDaPausa()),
                sessao.encerradaEm() == null ? null : utc(sessao.encerradaEm()),
                sessao.id());
        return sessao;
    }

    private int contarPerguntas(long sessaoId) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM mensagem WHERE sessao_id = ? AND autoria = 'TURMA'", Integer.class, sessaoId);
        return total == null ? 0 : total;
    }

    private static Sessao paraSessao(ResultSet linha, int numero) throws SQLException {
        String motivo = linha.getString("motivo_pausa");
        return new Sessao(
                linha.getLong("id"),
                linha.getLong("professor_id"),
                linha.getLong("turma_id"),
                linha.getLong("cenario_versao_id"),
                linha.getInt("limite_perguntas"),
                StatusDaSessao.valueOf(linha.getString("status")),
                motivo == null ? null : MotivoDaPausa.valueOf(motivo),
                instante(linha, "iniciada_em"),
                instante(linha, "encerrada_em"));
    }

    private static EventoDaSessao paraEvento(ResultSet linha) throws SQLException {
        String motivo = linha.getString("motivo");
        return new EventoDaSessao(
                EventoDaSessao.Tipo.valueOf(linha.getString("tipo")),
                motivo == null ? null : MotivoDaPausa.valueOf(motivo),
                instante(linha, "ocorrido_em"));
    }

    private static MensagemExibida paraMensagem(ResultSet linha) throws SQLException {
        return new MensagemExibida(
                MensagemExibida.Autoria.valueOf(linha.getString("autoria")),
                linha.getString("texto"),
                instante(linha, "criado_em"),
                linha.getBoolean("resposta_bloqueada"));
    }

    private static String nome(MotivoDaPausa motivo) {
        return motivo == null ? null : motivo.name();
    }

    private static Instant instante(ResultSet linha, String coluna) throws SQLException {
        OffsetDateTime valor = linha.getObject(coluna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
