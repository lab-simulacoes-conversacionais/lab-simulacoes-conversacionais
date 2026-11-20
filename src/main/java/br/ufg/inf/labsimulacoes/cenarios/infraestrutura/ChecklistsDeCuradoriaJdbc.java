package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.ChecklistsDeCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConferenciaDaCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ItemDoChecklist;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabela checklist_curadoria, uma linha por versão aprovada (RF-10, RN-06). */
@Repository
class ChecklistsDeCuradoriaJdbc implements ChecklistsDeCuradoria {

    private static final String INSERIR = """
            INSERT INTO checklist_curadoria (cenario_versao_id, curador_id, fatos_conferidos, linguagem_adequada,
                                             objetivos_coerentes, limites_respeitados, perguntas_de_teste)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";

    private static final String DA_VERSAO = """
            SELECT fatos_conferidos, linguagem_adequada, objetivos_coerentes, limites_respeitados, perguntas_de_teste
            FROM checklist_curadoria
            WHERE cenario_versao_id = ?""";

    private final JdbcTemplate jdbc;

    ChecklistsDeCuradoriaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(long versaoId, long curadorId, ConferenciaDaCuradoria conferencia) {
        if (!conferencia.completa()) {
            throw new IllegalArgumentException("Só se grava o checklist de uma aprovação com tudo conferido (RN-06)");
        }
        var marcados = conferencia.marcados();
        jdbc.update(
                INSERIR,
                versaoId,
                curadorId,
                marcados.contains(ItemDoChecklist.FATOS_CONFERIDOS),
                marcados.contains(ItemDoChecklist.LINGUAGEM_ADEQUADA),
                marcados.contains(ItemDoChecklist.OBJETIVOS_COERENTES),
                marcados.contains(ItemDoChecklist.LIMITES_RESPEITADOS),
                conferencia.perguntasDeTeste());
    }

    @Override
    public Optional<ConferenciaDaCuradoria> daVersao(long versaoId) {
        return jdbc.query(DA_VERSAO, (linha, numero) -> paraConferencia(linha), versaoId).stream().findFirst();
    }

    private static ConferenciaDaCuradoria paraConferencia(ResultSet linha) throws SQLException {
        var marcados = EnumSet.noneOf(ItemDoChecklist.class);
        marcar(marcados, linha.getBoolean("fatos_conferidos"), ItemDoChecklist.FATOS_CONFERIDOS);
        marcar(marcados, linha.getBoolean("linguagem_adequada"), ItemDoChecklist.LINGUAGEM_ADEQUADA);
        marcar(marcados, linha.getBoolean("objetivos_coerentes"), ItemDoChecklist.OBJETIVOS_COERENTES);
        marcar(marcados, linha.getBoolean("limites_respeitados"), ItemDoChecklist.LIMITES_RESPEITADOS);
        return new ConferenciaDaCuradoria(marcados, linha.getInt("perguntas_de_teste"));
    }

    private static void marcar(EnumSet<ItemDoChecklist> marcados, boolean marcado, ItemDoChecklist item) {
        if (marcado) {
            marcados.add(item);
        }
    }
}
