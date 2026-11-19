package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste.Desfecho;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntasDeTeste;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tabela pergunta_de_teste. O desfecho fica em duas colunas booleanas, e não num texto com CHECK, para
 * um desfecho novo não exigir trocar a restrição (RNF-12). A rodada em aberto é a das perguntas sem
 * devolução, e a ordem é a de gravação.
 */
@Repository
class PerguntasDeTesteJdbc implements PerguntasDeTeste {

    private static final String DA_RODADA = """
            SELECT id, cenario_versao_id, curador_id, pergunta, pergunta_bloqueada, resposta_bloqueada, resposta,
                   resposta_ia_meta_id
            FROM pergunta_de_teste
            WHERE cenario_versao_id = ? AND curador_id = ? AND devolucao_id IS NULL
            ORDER BY id""";

    private static final String CONTAR = """
            SELECT count(*)
            FROM pergunta_de_teste
            WHERE cenario_versao_id = ? AND curador_id = ? AND devolucao_id IS NULL AND NOT pergunta_bloqueada""";

    private final JdbcTemplate jdbc;

    PerguntasDeTesteJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PerguntaDeTeste gravar(PerguntaDeTeste pergunta) {
        Long id = jdbc.queryForObject("""
                INSERT INTO pergunta_de_teste (cenario_versao_id, curador_id, pergunta, pergunta_bloqueada,
                                               resposta_bloqueada, resposta, resposta_ia_meta_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id""",
                Long.class,
                pergunta.versaoId(),
                pergunta.curadorId(),
                pergunta.pergunta(),
                pergunta.desfecho() == Desfecho.PERGUNTA_BLOQUEADA,
                pergunta.desfecho() == Desfecho.RESPOSTA_BLOQUEADA,
                pergunta.resposta(),
                pergunta.respostaIaMetaId());
        return pergunta.comId(id);
    }

    @Override
    public List<PerguntaDeTeste> daRodada(long versaoId, long curadorId) {
        return jdbc.query(DA_RODADA, (linha, numero) -> paraPergunta(linha), versaoId, curadorId);
    }

    @Override
    public int contar(long versaoId, long curadorId) {
        Integer total = jdbc.queryForObject(CONTAR, Integer.class, versaoId, curadorId);
        return total == null ? 0 : total;
    }

    @Override
    public int fecharRodada(long versaoId, long devolucaoId) {
        return jdbc.update(
                "UPDATE pergunta_de_teste SET devolucao_id = ? WHERE cenario_versao_id = ? AND devolucao_id IS NULL",
                devolucaoId,
                versaoId);
    }

    private static PerguntaDeTeste paraPergunta(ResultSet linha) throws SQLException {
        Desfecho desfecho;
        if (linha.getBoolean("pergunta_bloqueada")) {
            desfecho = Desfecho.PERGUNTA_BLOQUEADA;
        } else if (linha.getBoolean("resposta_bloqueada")) {
            desfecho = Desfecho.RESPOSTA_BLOQUEADA;
        } else {
            desfecho = Desfecho.EXIBIDA;
        }
        return new PerguntaDeTeste(
                linha.getLong("id"),
                linha.getLong("cenario_versao_id"),
                linha.getLong("curador_id"),
                linha.getString("pergunta"),
                desfecho,
                linha.getString("resposta"),
                linha.getObject("resposta_ia_meta_id", Long.class));
    }
}
