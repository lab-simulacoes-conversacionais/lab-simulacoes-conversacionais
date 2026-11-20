package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.ia.ConsumoDaIa;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.ia.ResumoDoConsumo;
import br.ufg.inf.labsimulacoes.ia.dominio.Geracao;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.dominio.UsoDoModelo;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao.VersaoNoBanco;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * RNF-13: o custo de uma sessão soma todo o consumo dela, com o adaptador de IA real e só o provedor
 * simulado. Entram a resposta exibida, a que veio vazia e a bloqueada pelo filtro, embora só a exibida
 * vire mensagem. Sem {@code @Transactional}, como o {@link ConversaEmSalaTest}, e com sessões novas em
 * cada teste. O filtro simulado bloqueia todo texto com {@value #IMPROPRIO}.
 */
@SpringBootTest(properties = "lab.teste.contexto=custo-da-sessao")
@Import(InfraestruturaDeTeste.class)
class CustoDaSessaoTest {

    private static final String IMPROPRIO = "[impróprio]";
    private static final UsoDoModelo USO = new UsoDoModelo(100, 10);

    @MockitoBean
    private ProvedorDeIa provedor;

    @Autowired
    private InicioDeSessao inicio;

    @Autowired
    private ConversaDaSessao conversa;

    @Autowired
    private ConsumoDaIa consumo;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private VersaoNoBanco cenario;
    private long sessao;

    @BeforeEach
    void iniciarSessao() {
        dados = new DadosDeSessao(jdbc);
        String professora = dados.usuario("PROFESSOR");
        cenario = dados.cenarioAprovado(dados.idDe(dados.usuario("PROFESSOR")), dados.idDe(dados.usuario("CURADOR")));
        logar(professora);
        sessao = inicio.iniciar(dados.turma(), cenario.cenarioId()).id();
        when(provedor.nome()).thenReturn("openai");
        when(provedor.moderar(anyString())).thenAnswer(chamada -> ((String) chamada.getArgument(0)).contains(IMPROPRIO)
                ? new ResultadoDaModeracao(true, List.of("violence"))
                : ResultadoDaModeracao.aprovado());
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void custoDaSessaoSomaRespostaExibidaVaziaEBloqueada() {
        responde("Sou Santos Dumont.");
        conversa.enviarPergunta(sessao, "Quem é você?");
        conversa.responderPendente(sessao, new TelaQueAceitaBloqueio()).orElseThrow();

        responde(" ");
        conversa.enviarPergunta(sessao, "E o 14-bis?");
        assertThatThrownBy(() -> conversa.responderPendente(sessao, new TelaQueAceitaBloqueio()))
                .isInstanceOf(FalhaNoProvedorDeIa.class);

        responde("Uma frase " + IMPROPRIO + ".");
        assertThat(conversa.responderPendente(sessao, new TelaQueAceitaBloqueio())).isEmpty();

        ResumoDoConsumo resumo = consumo.daReferencia(FinalidadeDoConsumo.FALA_EM_SALA, sessao);
        assertThat(resumo.respostas()).isEqualTo(3);
        assertThat(resumo.tokensEntrada()).isEqualTo(300);
        assertThat(resumo.tokensSaida()).isEqualTo(30);
        assertThat(resumo.custo()).isPositive();
        assertThat(resumo.respostasSemCusto()).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem WHERE sessao_id = ? AND autoria = 'IA'", Integer.class, sessao))
                .isEqualTo(1);
    }

    /** O custo de uma sessão não leva o de outra. */
    @Test
    void custoDeOutraSessaoNaoEntra() {
        responde("Olá.");
        conversa.enviarPergunta(sessao, "Oi!");
        conversa.responderPendente(sessao, new TelaQueAceitaBloqueio());
        long outra = inicio.iniciar(dados.turma(), cenario.cenarioId()).id();
        conversa.enviarPergunta(outra, "Oi!");
        conversa.responderPendente(outra, new TelaQueAceitaBloqueio());

        assertThat(consumo.daReferencia(FinalidadeDoConsumo.FALA_EM_SALA, sessao).respostas()).isEqualTo(1);
        assertThat(consumo.daReferencia(FinalidadeDoConsumo.FALA_EM_SALA, outra).respostas()).isEqualTo(1);
    }

    private void responde(String texto) {
        doAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept(texto);
            return new Geracao<>(texto, "gpt-6.1-terra", Map.of(), USO);
        }).when(provedor).conversar(any(), any());
    }

    private static void logar(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(email, null, "ROLE_PROFESSOR"));
    }

    /** A tela simulada: o aviso de bloqueio só é registrado, e nenhuma frase bloqueada chega a ela. */
    private static final class TelaQueAceitaBloqueio implements TelaDaResposta {

        @Override
        public void exibir(String frase) {
            assertThat(frase).doesNotContain(IMPROPRIO);
        }

        @Override
        public void bloquear() {
            // A sessão já foi pausada e o caso, aberto.
        }
    }
}
