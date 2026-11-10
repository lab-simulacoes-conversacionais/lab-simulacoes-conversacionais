package br.ufg.inf.labsimulacoes.auditoria.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.FiltroDoLog;
import br.ufg.inf.labsimulacoes.auditoria.dominio.LogDeAuditoria;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gravação e leitura do log no banco real. O trigger contra UPDATE e DELETE é testado no
 * {@code EsquemaBaseTest}. Cada teste desfaz o que gravou, inclusive a desativação do trigger.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class LogDeAuditoriaJdbcTest {

    private static final Instant AGORA = Instant.parse("2026-11-08T13:45:10.123456Z");

    @Autowired
    private LogDeAuditoria log;

    @Autowired
    private JdbcTemplate jdbc;

    private long usuario;

    @BeforeEach
    void criarUsuario() {
        usuario = novoUsuario();
    }

    @Test
    void linhaGravadaVoltaIgualDoBanco() {
        RegistroDeAuditoria gravado = gravarEncadeado(Map.of(
                "versaoId", 3L,
                "comentario", "Falta a fonte \"primária\"",
                "perfis", List.of("CURADOR", "PROFESSOR"),
                "filtros", Map.of("turma", "8º A", "ativo", true)));

        RegistroDeAuditoria lido = ultimaLinha();

        assertThat(gravado.id()).isNotNull();
        assertThat(lido.id()).isEqualTo(gravado.id());
        assertThat(lido.usuarioId()).isEqualTo(usuario);
        assertThat(lido.acao()).isEqualTo(AcaoCritica.APROVAR_CENARIO);
        assertThat(lido.entidadeId()).isEqualTo("42");
        assertThat(lido.ocorridoEm()).isEqualTo(AGORA);
        assertThat(lido.hash()).isEqualTo(gravado.hash());
        assertThat(lido.detalhes()).containsOnlyKeys("versaoId", "comentario", "perfis", "filtros");
        assertThat(jdbc.queryForObject("SELECT entidade FROM log_auditoria WHERE id = ?", String.class, lido.id()))
                .isEqualTo("cenario");
    }

    @Test
    void hashContinuaConferindoDepoisDePassarPeloJsonb() {
        String anterior = log.travarEBuscarUltimoHash().orElse(null);
        gravarEncadeado(Map.of("filtros", Map.of("b", 2L, "a", "x"), "perfis", List.of("PROFESSOR")));

        assertThat(ultimaLinha().confereCom(anterior)).isTrue();
    }

    @Test
    void ultimoHashEODaLinhaMaisRecente() {
        gravarEncadeado(Map.of());
        RegistroDeAuditoria segunda = gravarEncadeado(Map.of());

        assertThat(log.travarEBuscarUltimoHash()).contains(segunda.hash());
    }

    @Test
    void linhasGravadasEmSequenciaFormamUmaCadeiaIntegra() {
        int antes = log.listarEmOrdem().size();
        gravarEncadeado(Map.of("n", 1L));
        gravarEncadeado(Map.of("n", 2L));
        gravarEncadeado(Map.of("n", 3L));

        assertThat(linhasDesde(antes)).hasSize(3);
        assertThat(primeiraQueNaoConfere(hashAntesDe(antes), linhasDesde(antes))).isEmpty();
    }

    @Test
    void alteracaoFeitaContornandoOTriggerQuebraACadeia() {
        int antes = log.listarEmOrdem().size();
        gravarEncadeado(Map.of("n", 1L));
        RegistroDeAuditoria alvo = gravarEncadeado(Map.of("n", 2L));
        gravarEncadeado(Map.of("n", 3L));

        jdbc.execute("ALTER TABLE log_auditoria DISABLE TRIGGER tg_log_auditoria_imutavel");
        jdbc.update("UPDATE log_auditoria SET detalhes = '{\"n\": 20}' WHERE id = ?", alvo.id());
        jdbc.execute("ALTER TABLE log_auditoria ENABLE TRIGGER tg_log_auditoria_imutavel");

        assertThat(primeiraQueNaoConfere(hashAntesDe(antes), linhasDesde(antes)))
                .hasValueSatisfying(adulterada -> assertThat(adulterada.id()).isEqualTo(alvo.id()));
    }

    @Test
    void consultaTrazSoAsAcoesDoUsuarioFiltrado() {
        long outro = novoUsuario();
        RegistroDeAuditoria doUsuario = gravar(usuario, AcaoCritica.APROVAR_CENARIO, AGORA);
        gravar(outro, AcaoCritica.APROVAR_CENARIO, AGORA);

        assertThat(ids(log.consultar(filtro(null, null, usuario, null)))).containsExactly(doUsuario.id());
    }

    @Test
    void consultaTrazSoAsAcoesDoTipoFiltrado() {
        gravar(usuario, AcaoCritica.APROVAR_CENARIO, AGORA);
        RegistroDeAuditoria devolucao = gravar(usuario, AcaoCritica.DEVOLVER_CENARIO, AGORA);

        assertThat(ids(log.consultar(filtro(null, null, usuario, AcaoCritica.DEVOLVER_CENARIO))))
                .containsExactly(devolucao.id());
    }

    @Test
    void periodoIncluiOInicioEDeixaOFimDeFora() {
        Instant inicio = AGORA;
        Instant fim = AGORA.plus(1, ChronoUnit.DAYS);
        gravar(usuario, AcaoCritica.APROVAR_CENARIO, inicio.minusNanos(1000));
        RegistroDeAuditoria noInicio = gravar(usuario, AcaoCritica.APROVAR_CENARIO, inicio);
        RegistroDeAuditoria antesDoFim = gravar(usuario, AcaoCritica.APROVAR_CENARIO, fim.minusNanos(1000));
        gravar(usuario, AcaoCritica.APROVAR_CENARIO, fim);

        assertThat(ids(log.consultar(filtro(inicio, fim, usuario, null))))
                .containsExactly(antesDoFim.id(), noInicio.id());
    }

    @Test
    void periodoAbertoEmUmaDasPontas() {
        RegistroDeAuditoria antiga = gravar(usuario, AcaoCritica.APROVAR_CENARIO, AGORA.minus(30, ChronoUnit.DAYS));
        RegistroDeAuditoria recente = gravar(usuario, AcaoCritica.APROVAR_CENARIO, AGORA);

        assertThat(ids(log.consultar(filtro(AGORA, null, usuario, null)))).containsExactly(recente.id());
        assertThat(ids(log.consultar(filtro(null, AGORA, usuario, null)))).containsExactly(antiga.id());
    }

    @Test
    void acaoMaisRecenteVemPrimeiroMesmoGravadaAntes() {
        RegistroDeAuditoria maisRecente = gravar(usuario, AcaoCritica.APROVAR_CENARIO, AGORA);
        RegistroDeAuditoria maisAntiga = gravar(usuario, AcaoCritica.APROVAR_CENARIO, AGORA.minusSeconds(60));
        RegistroDeAuditoria mesmaHoraGravadaDepois = gravar(usuario, AcaoCritica.APROVAR_CENARIO, AGORA);

        assertThat(ids(log.consultar(filtro(null, null, usuario, null))))
                .containsExactly(mesmaHoraGravadaDepois.id(), maisRecente.id(), maisAntiga.id());
    }

    @Test
    void consultaDevolveAPaginaPedida() {
        RegistroDeAuditoria primeira = gravar(usuario, AcaoCritica.APROVAR_CENARIO, AGORA.minusSeconds(3));
        RegistroDeAuditoria segunda = gravar(usuario, AcaoCritica.APROVAR_CENARIO, AGORA.minusSeconds(2));
        RegistroDeAuditoria terceira = gravar(usuario, AcaoCritica.APROVAR_CENARIO, AGORA.minusSeconds(1));

        assertThat(ids(log.consultar(new FiltroDoLog(null, null, usuario, null, 0, 2))))
                .containsExactly(terceira.id(), segunda.id());
        assertThat(ids(log.consultar(new FiltroDoLog(null, null, usuario, null, 1, 2))))
                .containsExactly(primeira.id());
        assertThat(log.consultar(new FiltroDoLog(null, null, usuario, null, 2, 2))).isEmpty();
    }

    @Test
    void linhaConsultadaVemCompleta() {
        RegistroDeAuditoria gravado = gravarEncadeado(Map.of("versaoId", 3L, "comentario", "Rever a fonte"));

        assertThat(log.consultar(filtro(null, null, usuario, null))).containsExactly(gravado);
    }

    @Test
    void semFiltroAConsultaTrazOLogInteiro() {
        RegistroDeAuditoria gravado = gravar(usuario, AcaoCritica.ALTERAR_PERFIS, Instant.now());

        assertThat(ids(log.consultar(FiltroDoLog.tudo()))).contains(gravado.id());
    }

    private long novoUsuario() {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Curadora', ?, 'hash') RETURNING id",
                Long.class, UUID.randomUUID() + "@escola.test");
    }

    private RegistroDeAuditoria gravar(long autor, AcaoCritica acao, Instant ocorridoEm) {
        String anterior = log.travarEBuscarUltimoHash().orElse(null);
        return log.gravar(RegistroDeAuditoria.novo(acao, autor, "42", Map.of(), ocorridoEm).encadeadoApos(anterior));
    }

    private static FiltroDoLog filtro(Instant inicio, Instant fim, Long usuarioId, AcaoCritica acao) {
        return new FiltroDoLog(inicio, fim, usuarioId, acao, 0, FiltroDoLog.TAMANHO_MAXIMO);
    }

    private static List<Long> ids(List<RegistroDeAuditoria> linhas) {
        return linhas.stream().map(RegistroDeAuditoria::id).toList();
    }

    private RegistroDeAuditoria gravarEncadeado(Map<String, ?> detalhes) {
        String anterior = log.travarEBuscarUltimoHash().orElse(null);
        return log.gravar(RegistroDeAuditoria.novo(AcaoCritica.APROVAR_CENARIO, usuario, "42", detalhes, AGORA)
                .encadeadoApos(anterior));
    }

    private RegistroDeAuditoria ultimaLinha() {
        List<RegistroDeAuditoria> todas = log.listarEmOrdem();
        return todas.get(todas.size() - 1);
    }

    /**
     * O banco é compartilhado com os outros testes, então a conferência começa na primeira linha
     * deste teste, a partir do hash da linha que já existia antes dele.
     */
    private static Optional<RegistroDeAuditoria> primeiraQueNaoConfere(
            String hashAnterior, List<RegistroDeAuditoria> linhas) {
        String anterior = hashAnterior;
        for (RegistroDeAuditoria linha : linhas) {
            if (!linha.confereCom(anterior)) {
                return Optional.of(linha);
            }
            anterior = linha.hash();
        }
        return Optional.empty();
    }

    private List<RegistroDeAuditoria> linhasDesde(int antes) {
        List<RegistroDeAuditoria> todas = log.listarEmOrdem();
        return todas.subList(antes, todas.size());
    }

    private String hashAntesDe(int antes) {
        return antes == 0 ? null : log.listarEmOrdem().get(antes - 1).hash();
    }
}
