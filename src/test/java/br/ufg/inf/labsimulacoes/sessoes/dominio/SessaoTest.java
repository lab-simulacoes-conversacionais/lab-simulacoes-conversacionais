package br.ufg.inf.labsimulacoes.sessoes.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Controles da sessão (RF-17), sem banco nem tela. */
class SessaoTest {

    private static final Instant INICIO = Instant.parse("2026-11-12T13:00:00Z");
    private static final Instant DEPOIS = INICIO.plusSeconds(60);

    @Test
    void sessaoComecaEmAndamentoLigadaAProfessorTurmaEVersao() {
        Sessao sessao = Sessao.iniciar(1, 2, 3, 40, INICIO);

        assertThat(sessao.id()).isNull();
        assertThat(sessao.professorId()).isEqualTo(1);
        assertThat(sessao.turmaId()).isEqualTo(2);
        assertThat(sessao.versaoId()).isEqualTo(3);
        assertThat(sessao.status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        assertThat(sessao.iniciadaEm()).isEqualTo(INICIO);
        assertThat(sessao.motivoDaPausa()).isNull();
        assertThat(sessao.encerradaEm()).isNull();
    }

    @Test
    void soQuemIniciouConduz() {
        Sessao sessao = emAndamento();

        assertThat(sessao.conduzidaPor(1)).isTrue();
        assertThat(sessao.conduzidaPor(9)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(MotivoDaPausa.class)
    void pausaGuardaOMotivoEGeraOEvento(MotivoDaPausa motivo) {
        MudancaNaSessao mudanca = emAndamento().pausar(motivo, DEPOIS);

        assertThat(mudanca.sessao().status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(mudanca.sessao().motivoDaPausa()).isEqualTo(motivo);
        assertThat(mudanca.evento()).isEqualTo(EventoDaSessao.pausa(motivo, DEPOIS));
    }

    @Test
    void pausadaNaoPausaDeNovo() {
        Sessao pausada = emAndamento().pausar(MotivoDaPausa.MANUAL, DEPOIS).sessao();

        assertThatThrownBy(() -> pausada.pausar(MotivoDaPausa.MANUAL, DEPOIS)).isInstanceOf(SessaoForaDoEstado.class);
    }

    /** RN-20: o bloqueio pausa a sessão em andamento, e quem retoma é o professor. */
    @Test
    void bloqueioPausaASessaoEmAndamento() {
        MudancaNaSessao mudanca = emAndamento().bloquearConteudo(DEPOIS).orElseThrow();

        assertThat(mudanca.sessao().status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(mudanca.sessao().motivoDaPausa()).isEqualTo(MotivoDaPausa.CONTEUDO_BLOQUEADO);
        assertThat(mudanca.evento()).isEqualTo(EventoDaSessao.pausa(MotivoDaPausa.CONTEUDO_BLOQUEADO, DEPOIS));
    }

    /** RN-20: pausada ou encerrada no meio da resposta, a sessão fica como está; só o caso é aberto. */
    @Test
    void bloqueioNaoMudaASessaoPausadaNemAEncerrada() {
        Sessao pausada = emAndamento().pausar(MotivoDaPausa.MANUAL, INICIO).sessao();
        Sessao encerrada = emAndamento().encerrar(INICIO);

        assertThat(pausada.bloquearConteudo(DEPOIS)).isEmpty();
        assertThat(encerrada.bloquearConteudo(DEPOIS)).isEmpty();
    }

    /** RN-25: até depois de uma suspensão, quem decide continuar é o professor. */
    @ParameterizedTest
    @EnumSource(MotivoDaPausa.class)
    void retomadaVoltaAoAndamentoSemMotivo(MotivoDaPausa motivo) {
        Sessao pausada = emAndamento().pausar(motivo, DEPOIS).sessao();

        MudancaNaSessao mudanca = pausada.retomar(DEPOIS.plusSeconds(30));

        assertThat(mudanca.sessao().status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        assertThat(mudanca.sessao().motivoDaPausa()).isNull();
        assertThat(mudanca.evento()).isEqualTo(EventoDaSessao.retomada(DEPOIS.plusSeconds(30)));
    }

    @Test
    void emAndamentoNaoRetoma() {
        assertThatThrownBy(() -> emAndamento().retomar(DEPOIS)).isInstanceOf(SessaoForaDoEstado.class);
    }

    /** RN-19: reiniciar só marca o ponto de corte, sem mudar o estado. */
    @Test
    void reinicioMantemOEstadoEMarcaOPontoDeCorte() {
        Sessao pausada = emAndamento().pausar(MotivoDaPausa.MANUAL, DEPOIS).sessao();

        MudancaNaSessao mudanca = pausada.reiniciar(DEPOIS.plusSeconds(5));

        assertThat(mudanca.sessao()).isEqualTo(pausada);
        assertThat(mudanca.evento()).isEqualTo(EventoDaSessao.reinicio(DEPOIS.plusSeconds(5)));
    }

    @Test
    void encerraEmAndamentoOuPausada() {
        Sessao encerrada = emAndamento().encerrar(DEPOIS);
        Sessao pausadaEncerrada = emAndamento().pausar(MotivoDaPausa.MANUAL, DEPOIS).sessao().encerrar(DEPOIS);

        assertThat(encerrada.status()).isEqualTo(StatusDaSessao.ENCERRADA);
        assertThat(encerrada.encerradaEm()).isEqualTo(DEPOIS);
        assertThat(pausadaEncerrada.status()).isEqualTo(StatusDaSessao.ENCERRADA);
        assertThat(pausadaEncerrada.motivoDaPausa()).isNull();
    }

    /** RN-19: sessão encerrada não pode ser reaberta nem controlada. */
    @Test
    void encerradaNaoAceitaNenhumControle() {
        Sessao encerrada = emAndamento().encerrar(DEPOIS);

        assertThatThrownBy(() -> encerrada.pausar(MotivoDaPausa.MANUAL, DEPOIS)).isInstanceOf(SessaoEncerrada.class);
        assertThatThrownBy(() -> encerrada.retomar(DEPOIS)).isInstanceOf(SessaoEncerrada.class);
        assertThatThrownBy(() -> encerrada.reiniciar(DEPOIS)).isInstanceOf(SessaoEncerrada.class);
        assertThatThrownBy(() -> encerrada.encerrar(DEPOIS)).isInstanceOf(SessaoEncerrada.class);
    }

    @Test
    void estadoIncoerenteERecusado() {
        assertThatThrownBy(() -> new Sessao(1L, 1, 2, 3, 40, StatusDaSessao.PAUSADA, null, INICIO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Sessao(1L, 1, 2, 3, 40, StatusDaSessao.EM_ANDAMENTO, MotivoDaPausa.MANUAL, INICIO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Sessao(1L, 1, 2, 3, 40, StatusDaSessao.ENCERRADA, null, INICIO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Sessao(1L, 1, 2, 3, 40, StatusDaSessao.EM_ANDAMENTO, null, INICIO, DEPOIS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** RN-18: até o limite a pergunta é aceita; a última possível também. */
    @Test
    void perguntaAbaixoDoLimiteEAceita() {
        Sessao sessao = Sessao.iniciar(1, 2, 3, 3, INICIO).comId(10);

        sessao.aceitarPergunta(estado(0, false));
        sessao.aceitarPergunta(estado(2, false));

        assertThat(sessao.perguntasRestantes(2)).isEqualTo(1);
    }

    /** RN-18: atingido o limite, o envio é bloqueado; reiniciar não zera a contagem (RN-19). */
    @Test
    void perguntaNoLimiteERecusada() {
        Sessao sessao = Sessao.iniciar(1, 2, 3, 3, INICIO).comId(10);

        assertThatThrownBy(() -> sessao.aceitarPergunta(estado(3, false)))
                .isInstanceOf(LimiteDePerguntasAtingido.class)
                .hasMessageContaining("3 perguntas");
        assertThat(sessao.perguntasRestantes(3)).isZero();
        assertThat(sessao.perguntasRestantes(5)).isZero();
    }

    /** Uma pergunta por vez: duas seguidas deixariam duas perguntas para uma resposta só. */
    @Test
    void perguntaComAAnteriorSemRespostaERecusada() {
        assertThatThrownBy(() -> emAndamento().aceitarPergunta(estado(1, true)))
                .isInstanceOf(PerguntaSemResposta.class);
    }

    @Test
    void sessaoPausadaNaoRecebePerguntaNemResponde() {
        Sessao pausada = emAndamento().pausar(MotivoDaPausa.MANUAL, DEPOIS).sessao();

        assertThatThrownBy(() -> pausada.aceitarPergunta(estado(0, false))).isInstanceOf(SessaoForaDoEstado.class);
        assertThatThrownBy(pausada::aceitarResposta).isInstanceOf(SessaoForaDoEstado.class);
    }

    @Test
    void sessaoEncerradaNaoRecebePerguntaNemResponde() {
        Sessao encerrada = emAndamento().encerrar(DEPOIS);

        assertThatThrownBy(() -> encerrada.aceitarPergunta(estado(0, false))).isInstanceOf(SessaoEncerrada.class);
        assertThatThrownBy(encerrada::aceitarResposta).isInstanceOf(SessaoEncerrada.class);
    }

    @Test
    void sessaoEmAndamentoResponde() {
        emAndamento().aceitarResposta();
    }

    /** RN-18: o limite vem dos parâmetros, que já exigem ao menos uma pergunta. */
    @Test
    void sessaoAceitaAoMenosUmaPergunta() {
        assertThatThrownBy(() -> Sessao.iniciar(1, 2, 3, 0, INICIO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Sessao.iniciar(1, 2, 3, 1, INICIO).limiteDePerguntas()).isEqualTo(1);
    }

    @Test
    void eventoSoTemMotivoNaPausa() {
        assertThatThrownBy(() -> new EventoDaSessao(EventoDaSessao.Tipo.PAUSA, null, INICIO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventoDaSessao(EventoDaSessao.Tipo.RETOMADA, MotivoDaPausa.MANUAL, INICIO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Estado com as perguntas já feitas e, se pedido, a última ainda sem resposta na tela. */
    private static EstadoDaSessao estado(int perguntasFeitas, boolean ultimaSemResposta) {
        List<MensagemExibida> tela = ultimaSemResposta
                ? List.of(new MensagemExibida(MensagemExibida.Autoria.TURMA, "Quem é você?", INICIO))
                : List.of();
        return new EstadoDaSessao(
                10, StatusDaSessao.EM_ANDAMENTO, null, INICIO, Duration.ZERO, null, null, perguntasFeitas, tela);
    }

    private static Sessao emAndamento() {
        return Sessao.iniciar(1, 2, 3, 40, INICIO).comId(10);
    }
}
