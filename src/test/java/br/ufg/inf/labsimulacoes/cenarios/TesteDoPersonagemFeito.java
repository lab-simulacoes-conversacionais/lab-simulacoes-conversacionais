package br.ufg.inf.labsimulacoes.cenarios;

import br.ufg.inf.labsimulacoes.cenarios.dominio.ItemDoChecklist;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntasDeTeste;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Para os testes que precisam de um cenário aprovado: grava, direto no banco, as perguntas de teste que
 * o curador faria no modo de teste (RF-09), sem chamar a IA. Com elas e o checklist inteiro, a aprovação
 * passa no RN-06. O modo de teste em si é testado no {@code TesteDoCenarioTest}.
 */
public final class TesteDoPersonagemFeito {

    /** O checklist inteiro marcado (RF-10). */
    public static final List<ItemDoChecklist> CHECKLIST = List.of(ItemDoChecklist.values());

    private TesteDoPersonagemFeito() {}

    /**
     * Grava as perguntas mínimas do RN-06, concluídas, na versão mais recente do cenário e na rodada em
     * aberto do curador.
     */
    public static void pelo(JdbcTemplate jdbc, String emailDoCurador, long cenarioId) {
        long versao = jdbc.queryForObject(
                "SELECT id FROM cenario_versao WHERE cenario_id = ? ORDER BY numero DESC LIMIT 1", Long.class, cenarioId);
        long curador = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, emailDoCurador);
        for (int i = 1; i <= PerguntasDeTeste.MINIMO_PARA_APROVAR; i++) {
            jdbc.update("""
                    INSERT INTO pergunta_de_teste (cenario_versao_id, curador_id, pergunta, situacao, resposta)
                    VALUES (?, ?, ?, 'CONCLUIDA', 'Resposta do personagem')""",
                    versao, curador, "Pergunta de teste " + i);
        }
    }
}
