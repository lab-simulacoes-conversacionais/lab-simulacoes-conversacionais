package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.MensagemDaConversa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao.VersaoNoBanco;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.LimiteDePerguntasAtingido;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida.Autoria;
import br.ufg.inf.labsimulacoes.sessoes.dominio.PerguntaSemResposta;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Pergunta da turma e resposta do personagem pelo caso de uso, com PostgreSQL e Redis reais e o
 * adaptador de IA simulado (RF-16, RN-11, RN-18, RNF-10).
 *
 * <p>Sem {@code @Transactional}, porque o estado só vai para o Redis depois da confirmação. Roda num
 * contexto só dele, como o {@link SessaoEmSalaTest}, e cada teste usa turma e usuários novos.
 */
@SpringBootTest(properties = "lab.teste.contexto=conversa-em-sala")
@Import(InfraestruturaDeTeste.class)
class ConversaEmSalaTest {

    private static final String LIMITE = "limite_perguntas_por_sessao";

    @MockitoBean
    private AdaptadorDeIa adaptador;

    @Autowired
    private InicioDeSessao inicio;

    @Autowired
    private ControlesDaSessao controles;

    @Autowired
    private ConversaDaSessao conversa;

    @Autowired
    private EstadosDasSessoes estados;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private String professora;
    private long sessao;

    @BeforeEach
    void iniciarSessao() {
        dados = new DadosDeSessao(jdbc);
        professora = dados.usuario("PROFESSOR");
        VersaoNoBanco cenario =
                dados.cenarioAprovado(dados.idDe(dados.usuario("PROFESSOR")), dados.idDe(dados.usuario("CURADOR")));
        logar(professora, "ROLE_PROFESSOR");
        sessao = inicio.iniciar(dados.turma(), cenario.cenarioId()).id();
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM parametro WHERE chave = ?", LIMITE);
    }

    /** RF-16: os trechos chegam na ordem e a pergunta e a resposta ficam no banco e no Redis. */
    @Test
    void perguntaDaTurmaERespostaEmTrechos() {
        respondeEmTrechos("Sou ", "Santos Dumont.");
        List<String> trechos = new ArrayList<>();

        PerguntaRecebida recebida = conversa.enviarPergunta(sessao, "  Quem é você?  ");
        MensagemExibida resposta = conversa.responderPendente(sessao, trechos::add).orElseThrow();

        assertThat(recebida.pergunta().autoria()).isEqualTo(Autoria.TURMA);
        assertThat(recebida.pergunta().texto()).isEqualTo("Quem é você?");
        assertThat(recebida.perguntasRestantes()).isEqualTo(39);
        assertThat(trechos).containsExactly("Sou ", "Santos Dumont.");
        assertThat(resposta.autoria()).isEqualTo(Autoria.IA);
        assertThat(resposta.texto()).isEqualTo("Sou Santos Dumont.");
        assertThat(jdbc.queryForList(
                        "SELECT autoria FROM mensagem WHERE sessao_id = ? ORDER BY criado_em, id", String.class, sessao))
                .containsExactly("TURMA", "IA");
        EstadoDaSessao estado = estados.buscar(sessao).orElseThrow();
        assertThat(estado.perguntasDaTurma()).isEqualTo(1);
        assertThat(estado.ultimasMensagens()).containsExactly(recebida.pergunta(), resposta);
    }

    /** RNF-10: a resposta exibida aponta para os metadados que o adaptador gravou. */
    @Test
    void respostaGuardaOsMetadados() {
        List<Long> metadados = respondeEmTrechos("Olá.");

        conversa.enviarPergunta(sessao, "Oi!");
        conversa.responderPendente(sessao, trecho -> {});

        assertThat(jdbc.queryForObject(
                        "SELECT resposta_ia_meta_id FROM mensagem WHERE sessao_id = ? AND autoria = 'IA'",
                        Long.class,
                        sessao))
                .isEqualTo(metadados.getFirst());
    }

    /** O prompt usa a versão da sessão, e a pergunta pendente não se repete no histórico. */
    @Test
    void pedidoTrazOCenarioDaSessaoEOHistorico() {
        respondeEmTrechos("Sou Santos Dumont.");
        conversa.enviarPergunta(sessao, "Quem é você?");
        conversa.responderPendente(sessao, trecho -> {});
        conversa.enviarPergunta(sessao, "E o 14-bis?");

        conversa.responderPendente(sessao, trecho -> {});

        PedidoDeFala pedido = ultimoPedido();
        assertThat(pedido.cenario().personagem()).isEqualTo("Santos Dumont");
        assertThat(pedido.cenario().contexto()).isEqualTo("Paris, 1906");
        assertThat(pedido.cenario().ano()).isEqualTo(8);
        assertThat(pedido.cenario().habilidades()).containsExactly("EF08HI03");
        assertThat(pedido.pergunta()).isEqualTo("E o 14-bis?");
        assertThat(pedido.historico()).containsExactly(
                new MensagemDaConversa(MensagemDaConversa.Autor.TURMA, "Quem é você?"),
                new MensagemDaConversa(MensagemDaConversa.Autor.PERSONAGEM, "Sou Santos Dumont."));
    }

    /** RN-19: depois de reiniciar, o histórico enviado ao modelo recomeça. */
    @Test
    void historicoComecaNoReinicio() {
        respondeEmTrechos("Sou Santos Dumont.");
        conversa.enviarPergunta(sessao, "Quem é você?");
        conversa.responderPendente(sessao, trecho -> {});
        controles.reiniciar(sessao);
        conversa.enviarPergunta(sessao, "Quem é você?");

        conversa.responderPendente(sessao, trecho -> {});

        assertThat(ultimoPedido().historico()).isEmpty();
    }

    /** A tela reconectou depois da resposta: não há o que responder e o modelo não é chamado. */
    @Test
    void semPerguntaPendenteNaoChamaOModelo() {
        assertThat(conversa.responderPendente(sessao, trecho -> {})).isEmpty();

        respondeEmTrechos("Olá.");
        conversa.enviarPergunta(sessao, "Oi!");
        conversa.responderPendente(sessao, trecho -> {});
        assertThat(conversa.responderPendente(sessao, trecho -> {})).isEmpty();
        verify(adaptador, times(1)).responder(any(), any());
    }

    /** RN-18: o limite guardado no início vale até o fim, e reiniciar não zera a contagem. */
    @Test
    void limiteDePerguntasBloqueiaONovoEnvio() {
        jdbc.update("INSERT INTO parametro (chave, valor) VALUES (?, '2')", LIMITE);
        long outra = inicio.iniciar(dados.turma(), novoCenario()).id();
        respondeEmTrechos("Resposta.");

        assertThat(conversa.enviarPergunta(outra, "Primeira").perguntasRestantes()).isEqualTo(1);
        conversa.responderPendente(outra, trecho -> {});
        controles.reiniciar(outra);
        assertThat(conversa.enviarPergunta(outra, "Segunda").perguntasRestantes()).isZero();
        conversa.responderPendente(outra, trecho -> {});

        assertThatThrownBy(() -> conversa.enviarPergunta(outra, "Terceira")).isInstanceOf(LimiteDePerguntasAtingido.class);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem WHERE sessao_id = ? AND autoria = 'TURMA'", Integer.class, outra))
                .isEqualTo(2);
    }

    /** RNF-04: se o provedor falha, a pergunta continua pendente e pode ser respondida de novo. */
    @Test
    void falhaDoProvedorDeixaAPerguntaPendente() {
        when(adaptador.responder(any(), any())).thenThrow(new FalhaNoProvedorDeIa("fora do ar", null));
        conversa.enviarPergunta(sessao, "Quem é você?");

        assertThatThrownBy(() -> conversa.responderPendente(sessao, trecho -> {}))
                .isInstanceOf(FalhaNoProvedorDeIa.class);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem WHERE sessao_id = ? AND autoria = 'IA'", Integer.class, sessao))
                .isZero();
        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Outra")).isInstanceOf(PerguntaSemResposta.class);

        reset(adaptador);
        respondeEmTrechos("Sou Santos Dumont.");
        assertThat(conversa.responderPendente(sessao, trecho -> {})).isPresent();
    }

    /** Uma pergunta por vez: duas seguidas, como um Enter repetido, ficariam com uma resposta só. */
    @Test
    void segundaPerguntaAntesDaRespostaERecusada() {
        conversa.enviarPergunta(sessao, "Quem é você?");

        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Quem é você?"))
                .isInstanceOf(PerguntaSemResposta.class);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem WHERE sessao_id = ? AND autoria = 'TURMA'", Integer.class, sessao))
                .isEqualTo(1);
    }

    /** Enquanto o personagem responde, uma nova pergunta espera, para as falas não se misturarem. */
    @Test
    void perguntaDuranteARespostaERecusada() {
        List<Throwable> recusas = new ArrayList<>();
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            try {
                conversa.enviarPergunta(sessao, "Outra pergunta");
            } catch (PerguntaSemResposta e) {
                recusas.add(e);
            }
            return new RespostaDoPersonagem("Olá.", metadados());
        });
        conversa.enviarPergunta(sessao, "Oi!");

        conversa.responderPendente(sessao, trecho -> {});

        assertThat(recusas).hasSize(1);
    }

    @Test
    void sessaoPausadaNaoRecebePerguntaNemResponde() {
        conversa.enviarPergunta(sessao, "Quem é você?");
        controles.pausar(sessao);

        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Oi")).isInstanceOf(SessaoForaDoEstado.class);
        assertThatThrownBy(() -> conversa.responderPendente(sessao, trecho -> {}))
                .isInstanceOf(SessaoForaDoEstado.class);
        verify(adaptador, never()).responder(any(), any());
    }

    @Test
    void sessaoEncerradaNaoRecebePergunta() {
        controles.encerrar(sessao);

        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Oi")).isInstanceOf(SessaoEncerrada.class);
    }

    @Test
    void perguntaVaziaERecusada() {
        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** RN-10: só o professor que iniciou a sessão conversa nela; o curador não conduz sessões. */
    @Test
    void soOProfessorDaSessaoConversa() {
        logar(dados.usuario("PROFESSOR"), "ROLE_PROFESSOR");
        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Oi")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> conversa.responderPendente(sessao, trecho -> {}))
                .isInstanceOf(AccessDeniedException.class);

        logar(professora, "ROLE_CURADOR");
        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Oi")).isInstanceOf(AccessDeniedException.class);
    }

    /**
     * O adaptador simulado repassa os trechos e devolve a resposta com uma linha nova de metadados a cada
     * chamada, como o real (RNF-10).
     *
     * @return os ids dos metadados gravados, na ordem das respostas
     */
    private List<Long> respondeEmTrechos(String... trechos) {
        List<Long> gravados = new ArrayList<>();
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            for (String trecho : trechos) {
                aoReceber.accept(trecho);
            }
            gravados.add(metadados());
            return new RespostaDoPersonagem(String.join("", trechos), gravados.getLast());
        });
        return gravados;
    }

    private long metadados() {
        return jdbc.queryForObject(
                "INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt) VALUES ('openai', 'm', 'v1') RETURNING id",
                Long.class);
    }

    private PedidoDeFala ultimoPedido() {
        ArgumentCaptor<PedidoDeFala> pedido = ArgumentCaptor.forClass(PedidoDeFala.class);
        verify(adaptador, atLeastOnce()).responder(pedido.capture(), any());
        return pedido.getValue();
    }

    private long novoCenario() {
        return dados.cenarioAprovado(dados.idDe(dados.usuario("PROFESSOR")), dados.idDe(dados.usuario("CURADOR")))
                .cenarioId();
    }

    private static void logar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }
}
