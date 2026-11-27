package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.auditoria.dominio.GravidadeFinal;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoJaTriada;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.eventos.SinalizacaoTriada;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

/**
 * O curador confirma ou descarta a sinalização (RF-24, RN-22), a triagem fica no log (RN-35) e sai o
 * evento sem os textos. O relógio é fixo numa quinta-feira, para o prazo de 2 dias úteis (RN-24); cada
 * teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
@RecordApplicationEvents
class TriagemDeSinalizacoesTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    /** Quinta-feira, 26 de novembro de 2026, às 10h na escola. */
    private static final Instant AGORA = naEscola("2026-11-26T10:00");
    /** Quarta: vence na sexta. */
    private static final Instant NO_PRAZO = naEscola("2026-11-25T14:00");
    /** Segunda: venceu na quarta. */
    private static final Instant ATRASADA = naEscola("2026-11-23T09:00");

    @MockitoBean
    private RelogioDaAuditoria relogio;

    @Autowired
    private TriagemDeSinalizacoes triagem;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry metricas;

    @Autowired
    private ApplicationEvents eventos;

    private SinalizacoesNoBanco banco;
    private String curadora;
    private long curadoraId;

    @BeforeEach
    void prepararSinalizacoes() {
        when(relogio.agora()).thenReturn(AGORA);
        banco = new SinalizacoesNoBanco(jdbc);
        var dados = new DadosDeSessao(jdbc);
        curadora = dados.usuario("CURADOR");
        curadoraId = dados.idDe(curadora);
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void curadoraConfirmaComAGravidadeFinal() {
        long id = banco.pendente(NO_PRAZO, "LEVE", "PROFESSOR");
        entrar(curadora, "ROLE_CURADOR");

        triagem.confirmar(id, GravidadeFinal.MODERADA);

        assertThat(linha(id))
                .containsEntry("status", "CONFIRMADA")
                .containsEntry("gravidade_final", "MODERADA")
                .containsEntry("triada_por_id", curadoraId);
        assertThat(jdbc.queryForObject("SELECT triada_em = ? FROM sinalizacao WHERE id = ?", Boolean.class,
                        AGORA.atOffset(ZoneOffset.UTC), id))
                .isTrue();
    }

    @Test
    void curadoraDescartaSemGravidade() {
        long id = banco.pendente(NO_PRAZO);
        entrar(curadora, "ROLE_CURADOR");

        triagem.descartar(id);

        assertThat(linha(id))
                .containsEntry("status", "DESCARTADA")
                .containsEntry("gravidade_final", null)
                .containsEntry("triada_por_id", curadoraId);
    }

    @Test
    void confirmarSemGravidadeNaoGravaNada() {
        long id = banco.pendente(NO_PRAZO);
        entrar(curadora, "ROLE_CURADOR");

        assertThatThrownBy(() -> triagem.confirmar(id, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escolha a gravidade final do erro confirmado");

        assertThat(linha(id)).containsEntry("status", "PENDENTE");
        assertThat(logDa(id)).isZero();
        assertThat(eventos.stream(SinalizacaoTriada.class)).isEmpty();
    }

    /** RN-35: a triagem fica no log com quem triou, quando e a decisão. */
    @Test
    void confirmacaoFicaNoLog() {
        long id = banco.pendente(NO_PRAZO, "LEVE", "PROFESSOR");
        entrar(curadora, "ROLE_CURADOR");

        triagem.confirmar(id, GravidadeFinal.GRAVE);

        Map<String, Object> registro = jdbc.queryForMap(
                """
                SELECT usuario_id, entidade, ocorrido_em = ? AS no_relogio, hash IS NOT NULL AS encadeado,
                       detalhes ->> 'decisao' AS decisao, detalhes ->> 'gravidadeFinal' AS gravidade_final,
                       detalhes ->> 'gravidadePercebida' AS gravidade_percebida, detalhes ->> 'perfil' AS perfil,
                       (detalhes ->> 'versaoId')::bigint AS versao, (detalhes ->> 'foraDoPrazo')::boolean AS atrasada
                FROM log_auditoria WHERE acao = 'TRIAR_SINALIZACAO' AND entidade_id = ?""",
                AGORA.atOffset(ZoneOffset.UTC), String.valueOf(id));
        assertThat(registro)
                .containsEntry("usuario_id", curadoraId)
                .containsEntry("entidade", "sinalizacao")
                .containsEntry("no_relogio", true)
                .containsEntry("encadeado", true)
                .containsEntry("decisao", "CONFIRMADA")
                .containsEntry("gravidade_final", "GRAVE")
                .containsEntry("gravidade_percebida", "LEVE")
                .containsEntry("perfil", "PROFESSOR")
                .containsEntry("versao", banco.versaoId)
                .containsEntry("atrasada", false);
    }

    /** RN-24: o log registra que a triagem saiu depois dos 2 dias úteis. */
    @Test
    void descarteForaDoPrazoFicaNoLogComoAtrasado() {
        long id = banco.pendente(ATRASADA);
        entrar(curadora, "ROLE_CURADOR");

        triagem.descartar(id);

        Map<String, Object> registro = jdbc.queryForMap(
                """
                SELECT detalhes ->> 'decisao' AS decisao, jsonb_exists(detalhes, 'gravidadeFinal') AS com_gravidade,
                       (detalhes ->> 'foraDoPrazo')::boolean AS atrasada
                FROM log_auditoria WHERE acao = 'TRIAR_SINALIZACAO' AND entidade_id = ?""",
                String.valueOf(id));
        assertThat(registro)
                .containsEntry("decisao", "DESCARTADA")
                .containsEntry("com_gravidade", false)
                .containsEntry("atrasada", true);
    }

    /** A métrica só conta a triagem confirmada no banco, com a decisão e o prazo. */
    @Test
    void metricaContaATriagemDepoisDoCommit() {
        long id = banco.pendente(ATRASADA);
        entrar(curadora, "ROLE_CURADOR");
        double antes = triagens("descartada", "fora");

        triagem.descartar(id);
        assertThat(triagens("descartada", "fora")).isEqualTo(antes);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThat(triagens("descartada", "fora")).isEqualTo(antes + 1);
    }

    @Test
    void triagemDesfeitaNaoEntraNaMetrica() {
        long id = banco.pendente(NO_PRAZO);
        entrar(curadora, "ROLE_CURADOR");
        double antes = triagens("confirmada", "dentro");

        triagem.confirmar(id, GravidadeFinal.LEVE);
        TestTransaction.flagForRollback();
        TestTransaction.end();

        assertThat(triagens("confirmada", "dentro")).isEqualTo(antes);
    }

    @Test
    void eventoSaiComADecisaoESemOsTextos() {
        long confirmada = banco.pendente(NO_PRAZO, "MODERADA", "PROFESSOR");
        long descartada = banco.pendente(NO_PRAZO, "GRAVE", "CURADOR");
        entrar(curadora, "ROLE_CURADOR");

        triagem.confirmar(confirmada, GravidadeFinal.LEVE);
        triagem.descartar(descartada);

        assertThat(eventos.stream(SinalizacaoTriada.class)).containsExactly(
                new SinalizacaoTriada(confirmada, banco.versaoId, "CONFIRMADA", "LEVE", "MODERADA", "PROFESSOR",
                        curadoraId, AGORA),
                new SinalizacaoTriada(descartada, banco.versaoId, "DESCARTADA", null, "GRAVE", "CURADOR",
                        curadoraId, AGORA));
    }

    @Test
    void sinalizacaoJaTriadaNaoETriadaDeNovo() {
        long id = banco.pendente(NO_PRAZO);
        entrar(curadora, "ROLE_CURADOR");
        triagem.descartar(id);

        assertThatThrownBy(() -> triagem.confirmar(id, GravidadeFinal.GRAVE)).isInstanceOf(SinalizacaoJaTriada.class);

        assertThat(linha(id)).containsEntry("status", "DESCARTADA");
        assertThat(logDa(id)).isEqualTo(1);
        assertThat(eventos.stream(SinalizacaoTriada.class)).hasSize(1);
    }

    @Test
    void sinalizacaoInexistenteNaoETriada() {
        entrar(curadora, "ROLE_CURADOR");

        assertThatThrownBy(() -> triagem.descartar(-1)).isInstanceOf(SinalizacaoNaoEncontrada.class);
        assertThat(eventos.stream(SinalizacaoTriada.class)).isEmpty();
    }

    /** Quem acumula os perfis de professor e curador tria (RN-36). */
    @Test
    void professorQueTambemECuradorTria() {
        long id = banco.pendente(NO_PRAZO);
        String dupla = new DadosDeSessao(jdbc).usuario("PROFESSOR", "CURADOR");
        entrar(dupla, "ROLE_PROFESSOR", "ROLE_CURADOR");

        triagem.descartar(id);

        assertThat(linha(id)).containsEntry("status", "DESCARTADA");
    }

    @Test
    void professorEAdministradorNaoTriam() {
        long id = banco.pendente(NO_PRAZO);

        entrar(new DadosDeSessao(jdbc).usuario("PROFESSOR"), "ROLE_PROFESSOR");
        assertThatThrownBy(() -> triagem.descartar(id)).isInstanceOf(AccessDeniedException.class);
        entrar(new DadosDeSessao(jdbc).usuario("ADMINISTRADOR"), "ROLE_ADMINISTRADOR");
        assertThatThrownBy(() -> triagem.confirmar(id, GravidadeFinal.LEVE)).isInstanceOf(AccessDeniedException.class);

        assertThat(linha(id)).containsEntry("status", "PENDENTE");
    }

    private Map<String, Object> linha(long id) {
        return jdbc.queryForMap("SELECT * FROM sinalizacao WHERE id = ?", id);
    }

    private long logDa(long id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM log_auditoria WHERE acao = 'TRIAR_SINALIZACAO' AND entidade_id = ?",
                Long.class, String.valueOf(id));
    }

    private double triagens(String decisao, String prazo) {
        Counter contador = metricas.find(TriagemDeSinalizacoes.METRICA)
                .tag("decisao", decisao).tag("prazo", prazo).counter();
        return contador == null ? 0 : contador.count();
    }

    private static void entrar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }

    private static Instant naEscola(String momento) {
        return LocalDateTime.parse(momento).atZone(FUSO).toInstant();
    }
}
