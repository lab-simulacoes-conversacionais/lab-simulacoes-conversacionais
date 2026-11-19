package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste.Situacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntasDeTeste;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tabela pergunta_de_teste. A rodada em aberto é a das perguntas sem devolução, e a ordem é a de
 * reserva. O resultado é gravado na mesma linha da reserva, sem tocar na devolução: o UPDATE do resultado
 * e o da devolução mudam colunas diferentes, e o banco aplica os dois, em qualquer ordem.
 */
@Repository
class PerguntasDeTesteJdbc implements PerguntasDeTeste {

    /*
     * Cada consulta é um texto fixo, sem montar SQL em tempo de execução; todo valor vai por parâmetro.
     * A rodada em aberto é a mesma condição em todas: cenario_versao_id, curador_id e devolucao_id nula.
     */

    private static final String RESERVAR = """
            INSERT INTO pergunta_de_teste (cenario_versao_id, curador_id, pergunta, situacao)
            VALUES (?, ?, ?, 'PENDENTE')
            RETURNING id""";

    private static final String CONCLUIR = """
            UPDATE pergunta_de_teste SET situacao = ?, resposta = ?, resposta_ia_meta_id = ?
            WHERE id = ? AND situacao = 'PENDENTE'
            RETURNING id, cenario_versao_id, curador_id, pergunta, situacao, resposta, resposta_ia_meta_id,
                      devolucao_id IS NOT NULL AS rodada_fechada""";

    private static final String DA_RODADA = """
            SELECT id, cenario_versao_id, curador_id, pergunta, situacao, resposta, resposta_ia_meta_id,
                   devolucao_id IS NOT NULL AS rodada_fechada
            FROM pergunta_de_teste
            WHERE cenario_versao_id = ? AND curador_id = ? AND devolucao_id IS NULL
            ORDER BY id""";

    /** Teto (RNF-13): todas as perguntas da rodada em aberto, de qualquer situação. */
    private static final String OCUPADAS = """
            SELECT count(*)
            FROM pergunta_de_teste
            WHERE cenario_versao_id = ? AND curador_id = ? AND devolucao_id IS NULL""";

    /** RN-06: só as que testaram o personagem, concluídas e de resposta bloqueada. */
    private static final String VALIDAS = """
            SELECT count(*)
            FROM pergunta_de_teste
            WHERE cenario_versao_id = ? AND curador_id = ? AND devolucao_id IS NULL
              AND situacao IN ('CONCLUIDA', 'RESPOSTA_BLOQUEADA')""";

    private static final String FECHAR_RODADA = """
            UPDATE pergunta_de_teste SET devolucao_id = ?
            WHERE cenario_versao_id = ? AND devolucao_id IS NULL""";

    private final JdbcTemplate jdbc;

    PerguntasDeTesteJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PerguntaDeTeste reservar(PerguntaDeTeste reservada) {
        if (reservada.situacao() != Situacao.PENDENTE || reservada.id() != null) {
            throw new IllegalStateException("Só uma pergunta nova e pendente é reservada");
        }
        Long id = jdbc.queryForObject(RESERVAR, Long.class, reservada.versaoId(), reservada.curadorId(), reservada.pergunta());
        return reservada.comId(id);
    }

    @Override
    public PerguntaDeTeste concluir(PerguntaDeTeste comResultado) {
        List<PerguntaDeTeste> atualizada = jdbc.query(CONCLUIR,
                (linha, numero) -> paraPergunta(linha),
                comResultado.situacao().name(), comResultado.resposta(), comResultado.respostaIaMetaId(),
                comResultado.id());
        if (atualizada.isEmpty()) {
            throw new IllegalStateException(
                    "A pergunta de teste " + comResultado.id() + " não existe ou já tem resultado");
        }
        return atualizada.getFirst();
    }

    @Override
    public List<PerguntaDeTeste> daRodada(long versaoId, long curadorId) {
        return jdbc.query(DA_RODADA, (linha, numero) -> paraPergunta(linha), versaoId, curadorId);
    }

    @Override
    public int ocupadas(long versaoId, long curadorId) {
        return contarCom(OCUPADAS, versaoId, curadorId);
    }

    @Override
    public int contar(long versaoId, long curadorId) {
        return contarCom(VALIDAS, versaoId, curadorId);
    }

    @Override
    public int fecharRodada(long versaoId, long devolucaoId) {
        return jdbc.update(FECHAR_RODADA, devolucaoId, versaoId);
    }

    /** @param consulta {@link #OCUPADAS} ou {@link #VALIDAS}, que só diferem na condição de situação */
    private int contarCom(String consulta, long versaoId, long curadorId) {
        Integer total = jdbc.queryForObject(consulta, Integer.class, versaoId, curadorId);
        return total == null ? 0 : total;
    }

    private static PerguntaDeTeste paraPergunta(ResultSet linha) throws SQLException {
        return new PerguntaDeTeste(
                linha.getLong("id"),
                linha.getLong("cenario_versao_id"),
                linha.getLong("curador_id"),
                linha.getString("pergunta"),
                Situacao.valueOf(linha.getString("situacao")),
                linha.getString("resposta"),
                linha.getObject("resposta_ia_meta_id", Long.class),
                linha.getBoolean("rodada_fechada"));
    }
}
