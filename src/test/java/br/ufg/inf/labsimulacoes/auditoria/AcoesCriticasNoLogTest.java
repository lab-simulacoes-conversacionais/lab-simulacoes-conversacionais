package br.ufg.inf.labsimulacoes.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.eventos.CenarioAprovado;
import br.ufg.inf.labsimulacoes.eventos.CenarioArquivado;
import br.ufg.inf.labsimulacoes.eventos.CenarioDevolvido;
import br.ufg.inf.labsimulacoes.eventos.CenarioReativado;
import br.ufg.inf.labsimulacoes.eventos.CenarioSuspenso;
import br.ufg.inf.labsimulacoes.eventos.RelatorioExportado;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Critério de aceite da issue 13: toda ação do RN-35 publicada como evento gera uma linha no log,
 * com usuário, data e hora e hash. Os eventos são publicados como os módulos de origem farão.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
class AcoesCriticasNoLogTest {

    /** Os ids dos cenários não precisam existir: entidade_id não tem chave estrangeira. */
    private static final AtomicLong CENARIO = new AtomicLong(System.nanoTime());

    @Autowired
    private ApplicationEventPublisher eventos;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JdbcTemplate jdbc;

    private long usuario;
    private Instant momento;

    @BeforeEach
    void criarUsuario() {
        usuario = jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Curadora', ?, 'hash') RETURNING id",
                Long.class, UUID.randomUUID() + "@escola.test");
        momento = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    void aprovacaoDeCenarioVaiParaOLog() throws InterruptedException {
        long cenario = CENARIO.incrementAndGet();
        publicar(new CenarioAprovado(cenario, 3, usuario, momento));

        Map<String, Object> linha = aguardarLinha("APROVAR_CENARIO", cenario);
        assertThat(linha.get("entidade")).isEqualTo("cenario");
        assertThat(linha.get("detalhes").toString()).isEqualTo("{\"versaoId\": 3}");
    }

    @Test
    void devolucaoDeCenarioVaiParaOLog() throws InterruptedException {
        long cenario = CENARIO.incrementAndGet();
        publicar(new CenarioDevolvido(cenario, 3, usuario, "Falta a fonte", momento));

        Map<String, Object> linha = aguardarLinha("DEVOLVER_CENARIO", cenario);
        assertThat(linha.get("detalhes").toString()).contains("\"comentario\": \"Falta a fonte\"");
    }

    @Test
    void arquivamentoDeCenarioVaiParaOLog() throws InterruptedException {
        long cenario = CENARIO.incrementAndGet();
        publicar(new CenarioArquivado(cenario, usuario, momento));

        aguardarLinha("ARQUIVAR_CENARIO", cenario);
    }

    @Test
    void suspensaoDeCenarioVaiParaOLog() throws InterruptedException {
        long cenario = CENARIO.incrementAndGet();
        publicar(new CenarioSuspenso(cenario, 3, usuario, "SINALIZACAO_GRAVE", momento));

        assertThat(aguardarLinha("SUSPENDER_CENARIO", cenario).get("detalhes").toString())
                .contains("\"motivo\": \"SINALIZACAO_GRAVE\"");
    }

    @Test
    void reativacaoDeCenarioVaiParaOLog() throws InterruptedException {
        long cenario = CENARIO.incrementAndGet();
        publicar(new CenarioReativado(cenario, 3, usuario, "SINALIZACAO_DESCARTADA", momento));

        aguardarLinha("REATIVAR_CENARIO", cenario);
    }

    @Test
    void exportacaoDeRelatorioVaiParaOLog() throws InterruptedException {
        publicar(new RelatorioExportado(usuario, "PDF", Map.of("turma", "8º A"), momento));

        List<Map<String, Object>> linhas = List.of();
        for (int tentativa = 0; tentativa < 100 && linhas.isEmpty(); tentativa++) {
            Thread.sleep(50);
            linhas = jdbc.queryForList("""
                    SELECT entidade, entidade_id, detalhes::text AS detalhes, hash
                    FROM log_auditoria WHERE acao = 'EXPORTAR_RELATORIO' AND usuario_id = ?""", usuario);
        }
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).get("entidade")).isEqualTo("relatorio");
        assertThat(linhas.get(0).get("entidade_id")).isNull();
        assertThat(linhas.get(0).get("detalhes").toString()).contains("\"formato\": \"PDF\"");
        assertThat(linhas.get(0).get("hash")).isNotNull();
    }

    private void publicar(Object evento) {
        transacao.executeWithoutResult(status -> eventos.publishEvent(evento));
    }

    /** O log é gravado depois da transação, em outra thread, como no sistema real. */
    private Map<String, Object> aguardarLinha(String acao, long entidadeId) throws InterruptedException {
        List<Map<String, Object>> linhas = List.of();
        for (int tentativa = 0; tentativa < 100 && linhas.isEmpty(); tentativa++) {
            Thread.sleep(50);
            linhas = jdbc.queryForList("""
                    SELECT usuario_id, entidade, detalhes::text AS detalhes, hash, ocorrido_em
                    FROM log_auditoria WHERE acao = ? AND entidade_id = ?""",
                    acao, String.valueOf(entidadeId));
        }
        assertThat(linhas).as("linha %s do log", acao).hasSize(1);
        Map<String, Object> linha = linhas.get(0);
        assertThat(linha.get("usuario_id")).isEqualTo(usuario);
        assertThat(((Timestamp) linha.get("ocorrido_em")).toInstant()).isEqualTo(momento);
        assertThat(linha.get("hash").toString()).matches("[0-9a-f]{64}");
        return linha;
    }
}
