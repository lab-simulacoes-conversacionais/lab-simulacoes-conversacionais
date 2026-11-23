package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.ChecklistsDeCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConferenciaDaCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ItemDoChecklist;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabela checklist_curadoria, uma linha por versão aprovada (RF-10, RN-06). */
@Repository
class ChecklistsDeCuradoriaJdbc implements ChecklistsDeCuradoria {

    private static final String INSERIR = """
            INSERT INTO checklist_curadoria (cenario_versao_id, curador_id, fatos_conferidos, linguagem_adequada,
                                             objetivos_coerentes, limites_respeitados, perguntas_de_teste)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";

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
}
