package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao.VersaoNoBanco;
import br.ufg.inf.labsimulacoes.sessoes.dominio.CenarioSemVersaoAprovada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.TurmaComSessaoEmAndamento;
import br.ufg.inf.labsimulacoes.sessoes.dominio.TurmaNaoEncontrada;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Início e controles da sessão pelos casos de uso, com perfis, PostgreSQL e Redis reais (RF-14, RF-17,
 * RN-10, RN-19, RNF-04).
 *
 * <p>Sem {@code @Transactional}, porque o estado só vai para o Redis depois da confirmação. O que
 * este teste confirma inclui versões aprovadas, que não podem ser apagadas (RN-08), então ele roda num
 * contexto só dele, com PostgreSQL e Redis próprios. Cada teste usa turmas e usuários novos.
 */
@SpringBootTest(properties = "lab.teste.contexto=sessao-em-sala")
@Import(InfraestruturaDeTeste.class)
class SessaoEmSalaTest {

    @Autowired
    private InicioDeSessao inicio;

    @Autowired
    private ControlesDaSessao controles;

    @Autowired
    private EstadosDasSessoes estados;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private String professora;
    private long turma;
    private VersaoNoBanco cenario;

    @BeforeEach
    void prepararProfessoraTurmaECenario() {
        dados = new DadosDeSessao(jdbc);
        professora = dados.usuario("PROFESSOR");
        turma = dados.turma();
        cenario = dados.cenarioAprovado(dados.idDe(dados.usuario("PROFESSOR")), dados.idDe(dados.usuario("CURADOR")));
        logar(professora, "ROLE_PROFESSOR");
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    /** RF-14: a sessão fica ligada a professor, turma e versão vigente, e o estado vai para o Redis. */
    @Test
    void professoraIniciaSessaoComAVersaoVigente() {
        Sessao sessao = inicio.iniciar(turma, cenario.cenarioId());

        assertThat(sessao.professorId()).isEqualTo(dados.idDe(professora));
        assertThat(sessao.turmaId()).isEqualTo(turma);
        assertThat(sessao.versaoId()).isEqualTo(cenario.versaoId());
        assertThat(sessao.status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        assertThat(estados.buscar(sessao.id())).contains(EstadoDaSessao.inicial(sessao));
    }

    @Test
    void turmaInexistenteERecusada() {
        assertThatThrownBy(() -> inicio.iniciar(-1, cenario.cenarioId())).isInstanceOf(TurmaNaoEncontrada.class);
    }

    /** RN-01: rascunho e cenário inexistente não vão para a sala. */
    @Test
    void cenarioNaoAprovadoERecusado() {
        long rascunho = dados.cenarioEmRascunho(dados.idDe(professora));

        assertThatThrownBy(() -> inicio.iniciar(turma, rascunho)).isInstanceOf(CenarioSemVersaoAprovada.class);
        assertThatThrownBy(() -> inicio.iniciar(turma, -1)).isInstanceOf(CenarioSemVersaoAprovada.class);
    }

    /** RN-10: a turma não pode ter duas sessões em andamento, nem de professores diferentes. */
    @Test
    void turmaComSessaoEmAndamentoNaoIniciaOutra() {
        inicio.iniciar(turma, cenario.cenarioId());
        logar(dados.usuario("PROFESSOR"), "ROLE_PROFESSOR");

        assertThatThrownBy(() -> inicio.iniciar(turma, cenario.cenarioId()))
                .isInstanceOf(TurmaComSessaoEmAndamento.class);
    }

    /**
     * Só a sessão em andamento bloqueia a turma (decisão da issue #12). Por isso a pausada não volta
     * enquanto a outra estiver em andamento.
     */
    @Test
    void pausadaNaoImpedeNovaSessaoMasNaoRetomaPorCimaDela() {
        long pausada = inicio.iniciar(turma, cenario.cenarioId()).id();
        controles.pausar(pausada);

        long outra = inicio.iniciar(turma, cenario.cenarioId()).id();

        assertThatThrownBy(() -> controles.retomar(pausada)).isInstanceOf(TurmaComSessaoEmAndamento.class);
        controles.encerrar(outra);
        assertThat(controles.retomar(pausada).status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
    }

    /** RF-17: pausa e retomada vão para evento_sessao e para o Redis. */
    @Test
    void pausarERetomarGravamOsEventosEAtualizamORedis() {
        long id = inicio.iniciar(turma, cenario.cenarioId()).id();

        EstadoDaSessao pausada = controles.pausar(id);
        assertThat(pausada.status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(pausada.motivoDaPausa()).isEqualTo(MotivoDaPausa.MANUAL);
        assertThat(estados.buscar(id)).contains(pausada);
        assertThatThrownBy(() -> controles.pausar(id)).isInstanceOf(SessaoForaDoEstado.class);

        EstadoDaSessao retomada = controles.retomar(id);
        assertThat(retomada.status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        assertThat(retomada.tempoEmPausa()).isPositive();
        assertThat(estados.buscar(id)).contains(retomada);

        assertThat(eventos(id)).containsExactly(
                Map.of("tipo", "PAUSA", "motivo", "MANUAL"), Map.of("tipo", "RETOMADA", "motivo", "-"));
        assertThat(jdbc.queryForObject("SELECT status FROM sessao WHERE id = ?", String.class, id))
                .isEqualTo("EM_ANDAMENTO");
    }

    /** RN-19: reiniciar limpa a tela, mas perguntas e respostas continuam no registro da sessão. */
    @Test
    void reiniciarLimpaATelaEMantemORegistro() {
        long id = inicio.iniciar(turma, cenario.cenarioId()).id();
        dados.mensagem(id, "TURMA", "Por que o 14-bis foi importante?");
        dados.mensagem(id, "IA", "Porque decolou por meios próprios.");
        redis.delete("sessao:" + id + ":estado");
        assertThat(controles.estado(id).ultimasMensagens()).hasSize(2);

        EstadoDaSessao reiniciada = controles.reiniciar(id);

        assertThat(reiniciada.ultimasMensagens()).isEmpty();
        assertThat(reiniciada.perguntasDaTurma()).isEqualTo(1);
        assertThat(reiniciada.reiniciadaEm()).isNotNull();
        assertThat(reiniciada.status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE sessao_id = ?", Integer.class, id))
                .isEqualTo(2);
        assertThat(eventos(id)).containsExactly(Map.of("tipo", "REINICIO", "motivo", "-"));
    }

    /** RN-19: encerrada não pode ser reaberta nem controlada. */
    @Test
    void encerradaNaoPodeSerReaberta() {
        long id = inicio.iniciar(turma, cenario.cenarioId()).id();
        controles.pausar(id);

        EstadoDaSessao encerrada = controles.encerrar(id);

        assertThat(encerrada.status()).isEqualTo(StatusDaSessao.ENCERRADA);
        assertThat(encerrada.tempoEmPausa()).isPositive();
        assertThat(jdbc.queryForObject("SELECT encerrada_em IS NOT NULL FROM sessao WHERE id = ?", Boolean.class, id))
                .isTrue();
        assertThatThrownBy(() -> controles.retomar(id)).isInstanceOf(SessaoEncerrada.class);
        assertThatThrownBy(() -> controles.pausar(id)).isInstanceOf(SessaoEncerrada.class);
        assertThatThrownBy(() -> controles.reiniciar(id)).isInstanceOf(SessaoEncerrada.class);
        assertThatThrownBy(() -> controles.encerrar(id)).isInstanceOf(SessaoEncerrada.class);
        assertThat(controles.estado(id).status()).isEqualTo(StatusDaSessao.ENCERRADA);
    }

    /** RNF-04: se a chave do Redis sumir, o estado é reconstruído a partir do PostgreSQL. */
    @Test
    void chaveApagadaEhReconstruidaDoBanco() {
        long id = inicio.iniciar(turma, cenario.cenarioId()).id();
        EstadoDaSessao pausada = controles.pausar(id);
        dados.mensagem(id, "TURMA", "Pergunta gravada pelo RF-16");
        redis.delete("sessao:" + id + ":estado");

        EstadoDaSessao reconstruido = controles.estado(id);

        assertThat(reconstruido.status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(reconstruido.motivoDaPausa()).isEqualTo(MotivoDaPausa.MANUAL);
        assertThat(reconstruido.pausadaDesde()).isEqualTo(pausada.pausadaDesde());
        assertThat(reconstruido.iniciadaEm()).isEqualTo(pausada.iniciadaEm());
        assertThat(reconstruido.perguntasDaTurma()).isEqualTo(1);
        assertThat(estados.buscar(id)).contains(reconstruido);
    }

    /** Uma chave que discorda do banco, porque a gravação no Redis falhou, é descartada. */
    @Test
    void chaveDesatualizadaEhRefeitaDoBanco() {
        Sessao sessao = inicio.iniciar(turma, cenario.cenarioId());
        controles.pausar(sessao.id());
        estados.gravar(EstadoDaSessao.inicial(sessao));

        assertThat(controles.estado(sessao.id()).status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(controles.retomar(sessao.id()).tempoEmPausa()).isPositive();
    }

    /**
     * O Redis é gravado depois da confirmação. Um controle logo após o reinício pode encontrar ali o
     * estado anterior, com o mesmo status, e não pode trazer de volta as mensagens que o reinício limpou.
     */
    @Test
    void controleNaoParteDeEstadoAnteriorAoReinicio() {
        long id = inicio.iniciar(turma, cenario.cenarioId()).id();
        dados.mensagem(id, "TURMA", "Por que o 14-bis foi importante?");
        redis.delete("sessao:" + id + ":estado");
        EstadoDaSessao antesDoReinicio = controles.estado(id);
        EstadoDaSessao reiniciada = controles.reiniciar(id);
        estados.gravar(antesDoReinicio);

        EstadoDaSessao pausada = controles.pausar(id);

        assertThat(pausada.ultimasMensagens()).isEmpty();
        assertThat(pausada.reiniciadaEm()).isEqualTo(reiniciada.reiniciadaEm());
        assertThat(pausada.perguntasDaTurma()).isEqualTo(1);
        assertThat(estados.buscar(id)).contains(pausada);
    }

    /** RN-10: só o professor que iniciou a sessão a vê e controla. */
    @Test
    void outroProfessorNaoControlaASessao() {
        long id = inicio.iniciar(turma, cenario.cenarioId()).id();
        logar(dados.usuario("PROFESSOR"), "ROLE_PROFESSOR");

        assertThatThrownBy(() -> controles.pausar(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controles.encerrar(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controles.estado(id)).isInstanceOf(AccessDeniedException.class);
    }

    /** RN-10: só o perfil Professor inicia e conduz sessões; curador e administrador não. */
    @Test
    void soOPerfilProfessorUsaASessao() {
        long id = inicio.iniciar(turma, cenario.cenarioId()).id();

        logar(dados.usuario("CURADOR"), "ROLE_CURADOR");
        assertThatThrownBy(() -> inicio.iniciar(dados.turma(), cenario.cenarioId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controles.pausar(id)).isInstanceOf(AccessDeniedException.class);

        logar(dados.usuario("ADMINISTRADOR"), "ROLE_ADMINISTRADOR");
        assertThatThrownBy(() -> inicio.iniciar(dados.turma(), cenario.cenarioId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semLoginNadaFunciona() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> inicio.iniciar(turma, cenario.cenarioId()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> controles.pausar(1)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void sessaoInexistenteERecusada() {
        assertThatThrownBy(() -> controles.pausar(-1)).isInstanceOf(SessaoNaoEncontrada.class);
        assertThatThrownBy(() -> controles.estado(-1)).isInstanceOf(SessaoNaoEncontrada.class);
    }

    private List<Map<String, String>> eventos(long sessaoId) {
        return jdbc.query(
                "SELECT tipo, COALESCE(motivo, '-') AS motivo FROM evento_sessao WHERE sessao_id = ? ORDER BY ocorrido_em, id",
                (linha, numero) -> Map.of("tipo", linha.getString("tipo"), "motivo", linha.getString("motivo")),
                sessaoId);
    }

    private static void logar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }
}
