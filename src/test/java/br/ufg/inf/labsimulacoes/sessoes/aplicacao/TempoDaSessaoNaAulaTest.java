package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao.VersaoNoBanco;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Pausa automática aos 50 minutos pelos casos de uso, com PostgreSQL e Redis reais (RF-19, RN-17). As
 * sessões são iniciadas e depois têm o início recuado no banco, para simular uma aula longa sem esperar.
 *
 * <p>Sem {@code @Transactional}, porque o estado só vai para o Redis depois da confirmação. Roda num
 * contexto só dele, como o {@link SessaoEmSalaTest}, porque grava versões aprovadas (RN-08).
 */
@SpringBootTest(properties = "lab.teste.contexto=tempo-da-sessao")
@Import(InfraestruturaDeTeste.class)
class TempoDaSessaoNaAulaTest {

    @Autowired
    private InicioDeSessao inicio;

    @Autowired
    private ControlesDaSessao controles;

    @Autowired
    private ConversaDaSessao conversa;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private long turma;
    private VersaoNoBanco cenario;

    @BeforeEach
    void prepararProfessoraTurmaECenario() {
        dados = new DadosDeSessao(jdbc);
        String professora = dados.usuario("PROFESSOR");
        turma = dados.turma();
        cenario = dados.cenarioAprovado(dados.idDe(dados.usuario("PROFESSOR")), dados.idDe(dados.usuario("CURADOR")));
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(professora, null, "ROLE_PROFESSOR"));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    /** Critério de aceite: pausa aos 50 minutos com o motivo registrado (RN-17). */
    @Test
    void aosCinquentaMinutosAPausaEGravadaComOMotivo() {
        long id = sessaoIniciadaHa(Duration.ofMinutes(53));

        EstadoDaSessao estado = controles.estado(id);

        assertThat(estado.status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(estado.motivoDaPausa()).isEqualTo(MotivoDaPausa.LIMITE_50_MINUTOS);
        Map<String, Object> pausa = jdbc.queryForMap(
                "SELECT tipo, motivo, ocorrido_em FROM evento_sessao WHERE sessao_id = ?", id);
        assertThat(pausa).containsEntry("tipo", "PAUSA").containsEntry("motivo", "LIMITE_50_MINUTOS");
        assertThat(jdbc.queryForObject("SELECT motivo_pausa FROM sessao WHERE id = ?", String.class, id))
                .isEqualTo("LIMITE_50_MINUTOS");
        // A pausa fica no momento dos 50 minutos, e não no da conferência.
        Instant iniciada = jdbc.queryForObject("SELECT iniciada_em FROM sessao WHERE id = ?", Timestamp.class, id)
                .toInstant();
        assertThat(((Timestamp) pausa.get("ocorrido_em")).toInstant()).isEqualTo(iniciada.plus(Duration.ofMinutes(50)));
    }

    @Test
    void antesDosCinquentaMinutosNadaMuda() {
        long id = sessaoIniciadaHa(Duration.ofMinutes(45));

        assertThat(controles.estado(id).status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_sessao WHERE sessao_id = ?", Integer.class, id))
                .isZero();
    }

    /** São 50 minutos de conversa: as pausas não contam (RF-19). */
    @Test
    void pausasNaoContamNosCinquentaMinutos() {
        long id = sessaoIniciadaHa(Duration.ofMinutes(60));
        Instant agora = Instant.now();
        evento(id, "PAUSA", "MANUAL", agora.minus(Duration.ofMinutes(40)));
        evento(id, "RETOMADA", null, agora.minus(Duration.ofMinutes(25)));

        assertThat(controles.estado(id).status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
    }

    /** A pergunta enviada depois dos 50 minutos pausa a sessão e é recusada, mesmo sem a tela ter lido antes. */
    @Test
    void perguntaDepoisDosCinquentaMinutosPausaERecusa() {
        long id = sessaoIniciadaHa(Duration.ofMinutes(51));

        assertThatThrownBy(() -> conversa.enviarPergunta(id, "Como o 14-bis decolou?"))
                .isInstanceOf(SessaoForaDoEstado.class);
        assertThat(controles.estado(id).motivoDaPausa()).isEqualTo(MotivoDaPausa.LIMITE_50_MINUTOS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE sessao_id = ?", Integer.class, id)).isZero();
    }

    /** Critério de aceite: com a confirmação do professor, a conversa continua e não pausa de novo. */
    @Test
    void depoisDeRetomarAConversaContinua() {
        long id = sessaoIniciadaHa(Duration.ofMinutes(55));
        controles.estado(id);

        EstadoDaSessao retomada = controles.retomar(id);

        assertThat(retomada.status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        assertThat(retomada.limiteDeTempoAtingido()).isTrue();
        assertThat(controles.estado(id).status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        redis.delete("sessao:" + id + ":estado");
        assertThat(controles.estado(id).status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
    }

    /**
     * O professor pausa depois dos 50 minutos, sem ninguém ter conferido: a pausa do limite entra
     * primeiro e já cumpre o pedido, sem erro e sem uma segunda pausa.
     */
    @Test
    void pausarDepoisDosCinquentaMinutosRegistraOLimite() {
        long id = sessaoIniciadaHa(Duration.ofMinutes(52));

        EstadoDaSessao pausada = controles.pausar(id);

        assertThat(pausada.motivoDaPausa()).isEqualTo(MotivoDaPausa.LIMITE_50_MINUTOS);
        assertThat(jdbc.queryForList("SELECT motivo FROM evento_sessao WHERE sessao_id = ?", String.class, id))
                .containsExactly("LIMITE_50_MINUTOS");
    }

    /** Encerrar depois dos 50 minutos sem conferência registra a pausa antes, e o tempo de conversa para nos 50. */
    @Test
    void encerrarDepoisDosCinquentaMinutosParaOTempoNosCinquenta() {
        long id = sessaoIniciadaHa(Duration.ofMinutes(55));

        EstadoDaSessao encerrada = controles.encerrar(id);

        assertThat(encerrada.status()).isEqualTo(StatusDaSessao.ENCERRADA);
        Instant encerradaEm = jdbc.queryForObject("SELECT encerrada_em FROM sessao WHERE id = ?", Timestamp.class, id)
                .toInstant();
        assertThat(encerrada.tempoDeConversaAte(encerradaEm)).isEqualTo(Duration.ofMinutes(50));
        assertThat(jdbc.queryForList("SELECT motivo FROM evento_sessao WHERE sessao_id = ?", String.class, id))
                .containsExactly("LIMITE_50_MINUTOS");
    }

    /**
     * Item 1 da revisão: pausa e retomada do professor depois dos 50 minutos, sem conferência. A pausa
     * do limite fica depois delas, e o estado refeito do banco concorda com a linha da sessão.
     */
    @Test
    void pausaTardiaNaoDesordenaOHistorico() {
        long id = sessaoIniciadaHa(Duration.ofMinutes(62));
        Instant agora = Instant.now();
        evento(id, "PAUSA", "MANUAL", agora.minus(Duration.ofMinutes(10)));
        evento(id, "RETOMADA", null, agora.minus(Duration.ofMinutes(2)));

        EstadoDaSessao estado = controles.estado(id);
        redis.delete("sessao:" + id + ":estado");
        EstadoDaSessao doBanco = controles.estado(id);

        assertThat(estado.status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(estado.motivoDaPausa()).isEqualTo(MotivoDaPausa.LIMITE_50_MINUTOS);
        assertThat(doBanco.status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(doBanco.motivoDaPausa()).isEqualTo(MotivoDaPausa.LIMITE_50_MINUTOS);
        assertThat(jdbc.queryForList(
                        "SELECT tipo || ':' || coalesce(motivo, '') FROM evento_sessao WHERE sessao_id = ? ORDER BY ocorrido_em, id",
                        String.class, id))
                .containsExactly("PAUSA:MANUAL", "RETOMADA:", "PAUSA:LIMITE_50_MINUTOS");
    }

    @Test
    void sessaoPausadaPeloProfessorNaoMudaDeMotivo() {
        long id = sessaoIniciadaHa(Duration.ofMinutes(30));
        controles.pausar(id);
        jdbc.update("UPDATE sessao SET iniciada_em = iniciada_em - interval '40 minutes' WHERE id = ?", id);

        assertThat(controles.estado(id).motivoDaPausa()).isEqualTo(MotivoDaPausa.MANUAL);
    }

    /** Inicia pelo caso de uso e recua o início no banco, apagando a chave do Redis, que tem o início antigo. */
    private long sessaoIniciadaHa(Duration tempo) {
        long id = inicio.iniciar(turma, cenario.cenarioId()).id();
        Instant iniciada = Instant.now().minus(tempo).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("UPDATE sessao SET iniciada_em = ? WHERE id = ?", iniciada.atOffset(ZoneOffset.UTC), id);
        redis.delete("sessao:" + id + ":estado");
        return id;
    }

    private void evento(long sessaoId, String tipo, String motivo, Instant em) {
        jdbc.update("INSERT INTO evento_sessao (sessao_id, tipo, motivo, ocorrido_em) VALUES (?, ?, ?, ?)",
                sessaoId, tipo, motivo, em.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
    }
}
