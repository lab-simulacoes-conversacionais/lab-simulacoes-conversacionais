package br.ufg.inf.labsimulacoes.auditoria.infraestrutura;

import br.ufg.inf.labsimulacoes.eventos.PerfilAlterado;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Registra no log a alteração de perfil (RN-35). Se a gravação falhar, o evento continua pendente no
 * registro de publicação e é reprocessado quando a aplicação reiniciar.
 */
@Component
class RegistroDePerfilAlterado {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    RegistroDePerfilAlterado(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @ApplicationModuleListener
    void registrar(PerfilAlterado evento) throws JsonProcessingException {
        String detalhes = json.writeValueAsString(Map.of(
                "perfisAnteriores", new TreeSet<>(evento.perfisAnteriores()),
                "perfisNovos", new TreeSet<>(evento.perfisNovos())));
        jdbc.update("""
                INSERT INTO log_auditoria (usuario_id, acao, entidade, entidade_id, detalhes, ocorrido_em)
                VALUES (?, 'ALTERAR_PERFIS', 'usuario', ?, ?::jsonb, ?)""",
                evento.alteradoPorId(),
                String.valueOf(evento.usuarioId()),
                detalhes,
                Timestamp.from(evento.ocorridoEm()));
    }
}
