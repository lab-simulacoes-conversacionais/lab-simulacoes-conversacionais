package br.ufg.inf.labsimulacoes.sessoes.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EventoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.HistoricoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.TurmaComSessaoEmAndamento;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Sessões e eventos no PostgreSQL real (RN-10, RN-19, RNF-04). Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class SessoesJdbcTest {

    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private Sessoes sessoes;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private long professor;
    private long turma;
    private long versao;

    @BeforeEach
    void prepararProfessorTurmaECenario() {
        dados = new DadosDeSessao(jdbc);
        professor = dados.idDe(dados.usuario("PROFESSOR"));
        long curador = dados.idDe(dados.usuario("CURADOR"));
        turma = dados.turma();
        versao = dados.cenarioAprovado(professor, curador).versaoId();
    }

    @Test
    void sessaoGravadaEhLidaIgual() {
        Sessao salva = sessoes.salvar(Sessao.iniciar(professor, turma, versao, 25, AGORA));

        assertThat(salva.id()).isNotNull();
        assertThat(sessoes.buscar(salva.id())).contains(salva);
        assertThat(sessoes.buscarParaAlterar(salva.id())).contains(salva);
        assertThat(sessoes.buscar(salva.id()).orElseThrow().limiteDePerguntas()).isEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT tipo FROM sessao WHERE id = ?", String.class, salva.id()))
                .isEqualTo("SIMULACAO");
    }

    @Test
    void mudancaDeEstadoEhGravada() {
        Sessao salva = sessoes.salvar(Sessao.iniciar(professor, turma, versao, 40, AGORA));

        Sessao pausada = sessoes.salvar(salva.pausar(MotivoDaPausa.CENARIO_SUSPENSO, AGORA).sessao());
        assertThat(sessoes.buscar(salva.id())).contains(pausada);

        Sessao encerrada = sessoes.salvar(pausada.encerrar(AGORA.plusSeconds(60)));
        assertThat(sessoes.buscar(salva.id())).contains(encerrada);
        assertThat(encerrada.status()).isEqualTo(StatusDaSessao.ENCERRADA);
    }

    @Test
    void sessaoInexistenteVemVazia() {
        assertThat(sessoes.buscar(-1)).isEmpty();
        assertThat(sessoes.historico(-1, 20)).isEmpty();
    }

    /** RN-10: o índice único recusa a segunda sessão em andamento da turma. */
    @Test
    void indiceDoBancoImpedeDuasSessoesEmAndamentoNaTurma() {
        sessoes.salvar(Sessao.iniciar(professor, turma, versao, 40, AGORA));

        assertThat(sessoes.temSessaoEmAndamento(turma)).isTrue();
        assertThatThrownBy(() -> sessoes.salvar(Sessao.iniciar(professor, turma, versao, 40, AGORA)))
                .isInstanceOf(TurmaComSessaoEmAndamento.class);
    }

    /** Pausada e encerrada não contam como em andamento (RN-10, decisão da issue #12). */
    @Test
    void pausadaEEncerradaNaoContamComoEmAndamento() {
        Sessao primeira = sessoes.salvar(Sessao.iniciar(professor, turma, versao, 40, AGORA));
        sessoes.salvar(primeira.pausar(MotivoDaPausa.MANUAL, AGORA).sessao());

        assertThat(sessoes.temSessaoEmAndamento(turma)).isFalse();
        Sessao segunda = sessoes.salvar(Sessao.iniciar(professor, turma, versao, 40, AGORA));
        sessoes.salvar(segunda.encerrar(AGORA));
        assertThat(sessoes.temSessaoEmAndamento(turma)).isFalse();
    }

    /** O índice também vale ao retomar: a pausada não volta se outra estiver em andamento. */
    @Test
    void retomarComOutraEmAndamentoERecusadoPeloBanco() {
        Sessao pausada = sessoes.salvar(
                sessoes.salvar(Sessao.iniciar(professor, turma, versao, 40, AGORA)).pausar(MotivoDaPausa.MANUAL, AGORA).sessao());
        sessoes.salvar(Sessao.iniciar(professor, turma, versao, 40, AGORA));

        assertThatThrownBy(() -> sessoes.salvar(pausada.retomar(AGORA).sessao()))
                .isInstanceOf(TurmaComSessaoEmAndamento.class);
    }

    /** RNF-04: eventos, contador e mensagens desde o último reinício reconstroem o estado. */
    @Test
    void historicoTrazEventosContadorEMensagensDesdeOReinicio() {
        Sessao sessao = sessoes.salvar(Sessao.iniciar(professor, turma, versao, 40, AGORA));
        dados.mensagem(sessao.id(), "TURMA", "Antes do reinício");
        dados.mensagem(sessao.id(), "IA", "Resposta antes do reinício");
        sessoes.registrar(sessao.id(), EventoDaSessao.pausa(MotivoDaPausa.MANUAL, AGORA.minusSeconds(20)));
        sessoes.registrar(sessao.id(), EventoDaSessao.retomada(AGORA.minusSeconds(10)));
        // O relógio do banco, e não o da JVM, porque as mensagens usam clock_timestamp().
        jdbc.execute("SELECT pg_sleep(0.01)");
        Instant reinicio = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
        sessoes.registrar(sessao.id(), EventoDaSessao.reinicio(reinicio));
        jdbc.execute("SELECT pg_sleep(0.01)");
        dados.mensagem(sessao.id(), "TURMA", "Depois do reinício");
        dados.mensagem(sessao.id(), "IA", "Resposta depois do reinício");

        HistoricoDaSessao historico = sessoes.historico(sessao.id(), EstadoDaSessao.MENSAGENS_GUARDADAS).orElseThrow();

        assertThat(historico.sessao()).isEqualTo(sessao);
        assertThat(historico.eventos()).containsExactly(
                EventoDaSessao.pausa(MotivoDaPausa.MANUAL, AGORA.minusSeconds(20)),
                EventoDaSessao.retomada(AGORA.minusSeconds(10)),
                EventoDaSessao.reinicio(reinicio));
        assertThat(historico.perguntasDaTurma()).isEqualTo(2);
        assertThat(historico.mensagensDesdeOReinicio()).extracting(MensagemExibida::texto)
                .containsExactly("Depois do reinício", "Resposta depois do reinício");
        assertThat(historico.mensagensDesdeOReinicio()).extracting(MensagemExibida::autoria)
                .containsExactly(MensagemExibida.Autoria.TURMA, MensagemExibida.Autoria.IA);
    }

    /** RN-11 e RNF-10: autoria genérica, e a resposta da IA aponta para os metadados dela. */
    @Test
    void mensagensGravadasVoltamNoHistorico() {
        Sessao sessao = sessoes.salvar(Sessao.iniciar(professor, turma, versao, 40, AGORA));
        long metadados = jdbc.queryForObject("""
                INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt) VALUES ('openai', 'm', 'v1')
                RETURNING id""", Long.class);
        var pergunta = new MensagemExibida(MensagemExibida.Autoria.TURMA, "Quem é você?", AGORA.plusSeconds(1));
        var resposta = new MensagemExibida(MensagemExibida.Autoria.IA, "Sou Santos Dumont.", AGORA.plusSeconds(3));

        sessoes.registrarMensagem(sessao.id(), pergunta, null);
        sessoes.registrarMensagem(sessao.id(), resposta, metadados);

        HistoricoDaSessao historico = sessoes.historico(sessao.id(), EstadoDaSessao.MENSAGENS_GUARDADAS).orElseThrow();
        assertThat(historico.mensagensDesdeOReinicio()).containsExactly(pergunta, resposta);
        assertThat(historico.perguntasDaTurma()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT resposta_ia_meta_id FROM mensagem WHERE sessao_id = ? AND autoria = 'IA'", Long.class, sessao.id()))
                .isEqualTo(metadados);
    }

    @Test
    void soARespostaDaIaTemMetadados() {
        Sessao sessao = sessoes.salvar(Sessao.iniciar(professor, turma, versao, 40, AGORA));
        var pergunta = new MensagemExibida(MensagemExibida.Autoria.TURMA, "Quem é você?", AGORA);
        var resposta = new MensagemExibida(MensagemExibida.Autoria.IA, "Sou Santos Dumont.", AGORA);

        // O @Repository traduz a IllegalArgumentException para a hierarquia de exceções do Spring.
        assertThatThrownBy(() -> sessoes.registrarMensagem(sessao.id(), pergunta, 1L))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RNF-10");
        assertThatThrownBy(() -> sessoes.registrarMensagem(sessao.id(), resposta, null))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RNF-10");
    }

    /** Só as mais recentes voltam, mas em ordem de exibição. */
    @Test
    void historicoLimitaAsMensagensAsMaisRecentes() {
        Sessao sessao = sessoes.salvar(Sessao.iniciar(professor, turma, versao, 40, AGORA));
        for (int i = 1; i <= 5; i++) {
            dados.mensagem(sessao.id(), "TURMA", "P" + i);
        }

        HistoricoDaSessao historico = sessoes.historico(sessao.id(), 3).orElseThrow();

        assertThat(historico.mensagensDesdeOReinicio()).extracting(MensagemExibida::texto).containsExactly("P3", "P4", "P5");
        assertThat(historico.perguntasDaTurma()).isEqualTo(5);
    }
}
