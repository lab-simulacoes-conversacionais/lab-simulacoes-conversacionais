package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Retomada depois de recarga, queda de conexão ou falha do provedor (RF-20, RNF-04), pelo caso de uso,
 * com PostgreSQL e Redis reais e o adaptador de IA simulado. Mesmo contexto do
 * {@link ConversaEmSalaTest}.
 */
@SpringBootTest(properties = "lab.teste.contexto=conversa-em-sala")
@Import(InfraestruturaDeTeste.class)
class RetomadaAposFalhaTest {

    private static final String BLOQUEIO = "<bloqueio>";

    @MockitoBean
    private AdaptadorDeIa adaptador;

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

    private Authentication professora;
    private long sessao;

    @BeforeEach
    void iniciarSessao() {
        var dados = new DadosDeSessao(jdbc);
        professora = new TestingAuthenticationToken(dados.usuario("PROFESSOR"), null, "ROLE_PROFESSOR");
        long cenario = dados.cenarioAprovado(dados.idDe(dados.usuario("PROFESSOR")), dados.idDe(dados.usuario("CURADOR")))
                .cenarioId();
        SecurityContextHolder.getContext().setAuthentication(professora);
        sessao = inicio.iniciar(dados.turma(), cenario).id();
        when(adaptador.moderar(anyString())).thenReturn(ResultadoDaModeracao.aprovado());
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    /** A tela recarregada depois de a resposta terminar recebe a resposta gravada, sem chamar o modelo de novo. */
    @Test
    void telaQueVoltaDepoisDaRespostaRecebeARespostaGravada() {
        respondeCom("Foi em 1906.");
        conversa.enviarPergunta(sessao, "Quando foi o voo?");
        conversa.responderPendente(sessao, new Tela());
        var recarregada = new Tela();

        conversa.entregarResposta(sessao, recarregada);

        assertThat(recarregada.eventos).containsExactly("Foi em 1906.");
        verify(adaptador, times(1)).responder(any(), any());
    }

    /**
     * Critério de aceite: recarregar a página no meio da resposta não perde a conversa. O canal novo
     * espera a resposta do canal antigo terminar e recebe ela inteira, sem uma segunda chamada ao modelo.
     */
    @Test
    void telaQueRecarregaNoMeioDaRespostaRecebeARespostaInteira() throws Exception {
        var noMeio = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept("Foi em 1906, ");
            noMeio.countDown();
            liberar.await(10, TimeUnit.SECONDS);
            aoReceber.accept("em Paris.");
            return new RespostaDoPersonagem("Foi em 1906, em Paris.", metadados());
        });
        conversa.enviarPergunta(sessao, "Quando foi o voo?");
        var antiga = new Tela();
        CompletableFuture<Void> primeira = comProfessora(() -> conversa.entregarResposta(sessao, antiga));
        assertThat(noMeio.await(10, TimeUnit.SECONDS)).isTrue();

        var recarregada = new Tela();
        CompletableFuture<Void> segunda = comProfessora(() -> conversa.entregarResposta(sessao, recarregada));
        Thread.sleep(300);
        assertThat(segunda).isNotDone();
        liberar.countDown();
        primeira.get(10, TimeUnit.SECONDS);
        segunda.get(10, TimeUnit.SECONDS);

        assertThat(recarregada.eventos).containsExactly("Foi em 1906, em Paris.");
        verify(adaptador, times(1)).responder(any(), any());
    }

    /** Critério de aceite: a falha do provedor permite reenviar; a pergunta pendente é respondida na volta. */
    @Test
    void perguntaQueFicouSemRespostaEhRespondidaNaVolta() {
        when(adaptador.responder(any(), any())).thenThrow(new FalhaNoProvedorDeIa("fora do ar", null));
        conversa.enviarPergunta(sessao, "Quando foi o voo?");
        assertThatThrownBy(() -> conversa.entregarResposta(sessao, new Tela())).isInstanceOf(FalhaNoProvedorDeIa.class);

        respondeCom("Foi em 1906.");
        var novaTentativa = new Tela();
        conversa.entregarResposta(sessao, novaTentativa);

        assertThat(novaTentativa.eventos).containsExactly("Foi em 1906.");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem WHERE sessao_id = ? AND autoria = 'IA'", Integer.class, sessao))
                .isEqualTo(1);
    }

    /** RN-20: se a resposta foi bloqueada enquanto a tela estava fora, a volta só mostra o aviso. */
    @Test
    void respostaBloqueadaEnquantoATelaEstavaForaSoAvisa() {
        when(adaptador.moderar("Foi em 1906.")).thenReturn(new ResultadoDaModeracao(true, List.of("violence")));
        respondeCom("Foi em 1906.");
        conversa.enviarPergunta(sessao, "Quando foi o voo?");
        conversa.responderPendente(sessao, new Tela());
        var recarregada = new Tela();

        conversa.entregarResposta(sessao, recarregada);

        assertThat(recarregada.eventos).containsExactly(BLOQUEIO);
    }

    @Test
    void semConversaNaoEntregaNada() {
        var tela = new Tela();

        conversa.entregarResposta(sessao, tela);

        assertThat(tela.eventos).isEmpty();
    }

    /** Critério de aceite: o Redis reiniciado, sem a chave, não perde a sessão nem a conversa. */
    @Test
    void semAChaveDoRedisASessaoEAConversaContinuam() {
        respondeCom("Foi em 1906.");
        conversa.enviarPergunta(sessao, "Quando foi o voo?");
        conversa.responderPendente(sessao, new Tela());
        controles.pausar(sessao);
        redis.delete("sessao:" + sessao + ":estado");

        var estado = controles.estado(sessao);

        assertThat(estado.status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(estado.perguntasDaTurma()).isEqualTo(1);
        assertThat(estado.ultimasMensagens()).extracting(mensagem -> mensagem.texto())
                .containsExactly("Quando foi o voo?", "Foi em 1906.");
        await().atMost(Duration.ofSeconds(5)).until(() -> redis.hasKey("sessao:" + sessao + ":estado"));
    }

    private CompletableFuture<Void> comProfessora(Runnable acao) {
        return CompletableFuture.runAsync(() -> {
            SecurityContextHolder.getContext().setAuthentication(professora);
            try {
                acao.run();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private void respondeCom(String texto) {
        doAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept(texto);
            return new RespostaDoPersonagem(texto, metadados());
        }).when(adaptador).responder(any(), any());
    }

    private long metadados() {
        return jdbc.queryForObject(
                "INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt) VALUES ('openai', 'm', 'v1') RETURNING id",
                Long.class);
    }

    /** Tela simulada: guarda as frases exibidas e marca o bloqueio, que não traz texto. */
    private static final class Tela implements TelaDaResposta {

        private final List<String> eventos = new CopyOnWriteArrayList<>();

        @Override
        public void exibir(String frase) {
            eventos.add(frase);
        }

        @Override
        public void bloquear() {
            eventos.add(BLOQUEIO);
        }
    }
}
