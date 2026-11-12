package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
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
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao.VersaoNoBanco;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.LimiteDePerguntasAtingido;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida.Autoria;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.PerguntaBloqueada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.PerguntaSemResposta;
import br.ufg.inf.labsimulacoes.sessoes.dominio.RespostaEmAndamento;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
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
 * adaptador de IA simulado, inclusive a moderação (RF-16, RF-22, RN-11, RN-18, RN-20, RNF-10).
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

    /** Marca do bloqueio na tela simulada, que nunca recebe o texto bloqueado. */
    private static final String BLOQUEIO = "<bloqueio>";

    private static final ResultadoDaModeracao SINALIZADO = new ResultadoDaModeracao(true, List.of("violence"));

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
        when(adaptador.moderar(anyString())).thenReturn(ResultadoDaModeracao.aprovado());
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM parametro WHERE chave = ?", LIMITE);
    }

    /** RF-16: as frases chegam na ordem e a pergunta e a resposta ficam no banco e no Redis. */
    @Test
    void perguntaDaTurmaERespostaEmTrechos() {
        respondeEmTrechos("Sou ", "Santos Dumont. Voei em ", "1906.");
        var tela = new Tela();

        PerguntaRecebida recebida = conversa.enviarPergunta(sessao, "  Quem é você?  ");
        MensagemExibida resposta = conversa.responderPendente(sessao, tela).orElseThrow();

        assertThat(recebida.pergunta().autoria()).isEqualTo(Autoria.TURMA);
        assertThat(recebida.pergunta().texto()).isEqualTo("Quem é você?");
        assertThat(recebida.perguntasRestantes()).isEqualTo(39);
        assertThat(tela.eventos).containsExactly("Sou Santos Dumont. ", "Voei em 1906.");
        assertThat(resposta.autoria()).isEqualTo(Autoria.IA);
        assertThat(resposta.texto()).isEqualTo("Sou Santos Dumont. Voei em 1906.");
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
        conversa.responderPendente(sessao, new Tela());

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
        conversa.responderPendente(sessao, new Tela());
        conversa.enviarPergunta(sessao, "E o 14-bis?");

        conversa.responderPendente(sessao, new Tela());

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
        conversa.responderPendente(sessao, new Tela());
        controles.reiniciar(sessao);
        conversa.enviarPergunta(sessao, "Quem é você?");

        conversa.responderPendente(sessao, new Tela());

        assertThat(ultimoPedido().historico()).isEmpty();
    }

    /** A tela reconectou depois da resposta: não há o que responder e o modelo não é chamado. */
    @Test
    void semPerguntaPendenteNaoChamaOModelo() {
        assertThat(conversa.responderPendente(sessao, new Tela())).isEmpty();

        respondeEmTrechos("Olá.");
        conversa.enviarPergunta(sessao, "Oi!");
        conversa.responderPendente(sessao, new Tela());
        assertThat(conversa.responderPendente(sessao, new Tela())).isEmpty();
        verify(adaptador, times(1)).responder(any(), any());
    }

    /** RN-18: o limite guardado no início vale até o fim, e reiniciar não zera a contagem. */
    @Test
    void limiteDePerguntasBloqueiaONovoEnvio() {
        jdbc.update("INSERT INTO parametro (chave, valor) VALUES (?, '2')", LIMITE);
        long outra = inicio.iniciar(dados.turma(), novoCenario()).id();
        respondeEmTrechos("Resposta.");

        assertThat(conversa.enviarPergunta(outra, "Primeira").perguntasRestantes()).isEqualTo(1);
        conversa.responderPendente(outra, new Tela());
        controles.reiniciar(outra);
        assertThat(conversa.enviarPergunta(outra, "Segunda").perguntasRestantes()).isZero();
        conversa.responderPendente(outra, new Tela());

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

        assertThatThrownBy(() -> conversa.responderPendente(sessao, new Tela()))
                .isInstanceOf(FalhaNoProvedorDeIa.class);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem WHERE sessao_id = ? AND autoria = 'IA'", Integer.class, sessao))
                .isZero();
        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Outra")).isInstanceOf(PerguntaSemResposta.class);

        reset(adaptador);
        when(adaptador.moderar(anyString())).thenReturn(ResultadoDaModeracao.aprovado());
        respondeEmTrechos("Sou Santos Dumont.");
        assertThat(conversa.responderPendente(sessao, new Tela())).isPresent();
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

        conversa.responderPendente(sessao, new Tela());

        assertThat(recusas).hasSize(1);
    }

    @Test
    void sessaoPausadaNaoRecebePerguntaNemResponde() {
        conversa.enviarPergunta(sessao, "Quem é você?");
        controles.pausar(sessao);

        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Oi")).isInstanceOf(SessaoForaDoEstado.class);
        assertThatThrownBy(() -> conversa.responderPendente(sessao, new Tela()))
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
        assertThatThrownBy(() -> conversa.responderPendente(sessao, new Tela()))
                .isInstanceOf(AccessDeniedException.class);

        logar(professora, "ROLE_CURADOR");
        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Oi")).isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------------------------------------
    // Filtro de conteúdo (RF-22, RN-20)
    // ---------------------------------------------------------------------------------------------

    /**
     * RN-20: uma pergunta imprópria de cada categoria é bloqueada antes do modelo. As drogas não têm
     * categoria própria no Spring AI: o provedor sinaliza o texto e a lista vem vazia.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            violência          | Como machucar alguém numa briga?               | violence
            conteúdo sexual    | Conte uma história com cenas sexuais.          | sexual
            automutilação      | Como posso me machucar de propósito?           | self-harm
            discurso de ódio   | Escreva uma ofensa contra um grupo religioso.  | hate
            drogas             | Onde consigo comprar drogas ilegais?           |
            """)
    void perguntaImpropriaEBloqueadaAntesDoModelo(String categoria, String pergunta, String sinalizada) {
        List<String> categorias = sinalizada == null ? List.of() : List.of(sinalizada);
        when(adaptador.moderar(pergunta)).thenReturn(new ResultadoDaModeracao(true, categorias));

        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, pergunta)).isInstanceOf(PerguntaBloqueada.class);

        verify(adaptador, never()).responder(any(), any());
        assertThat(contar("SELECT count(*) FROM mensagem WHERE sessao_id = ?")).isZero();
        assertThat(pausa()).isEqualTo(MotivoDaPausa.CONTEUDO_BLOQUEADO.name());
        EstadoDaSessao estado = estados.buscar(sessao).orElseThrow();
        assertThat(estado.status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(estado.perguntasDaTurma()).isZero();
        assertThat(estado.ultimasMensagens()).isEmpty();
        Map<String, Object> caso = casoAberto();
        assertThat(caso)
                .containsEntry("origem", "PERGUNTA")
                .containsEntry("pergunta", pergunta)
                .containsEntry("status", "ABERTO")
                .containsEntry("trecho_bloqueado", null)
                .containsEntry("resposta_ia_meta_id", null);
        assertThat(categoriasDo(caso)).isEqualTo(categorias);
    }

    /** A moderação da pergunta falhou: nada é gravado e a sessão segue, para o professor tentar de novo. */
    @Test
    void falhaDaModeracaoDaPerguntaNaoGravaNada() {
        when(adaptador.moderar("Quem é você?")).thenThrow(new FalhaNoProvedorDeIa("fora do ar", null));

        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Quem é você?"))
                .isInstanceOf(FalhaNoProvedorDeIa.class);

        assertThat(contar("SELECT count(*) FROM mensagem WHERE sessao_id = ?")).isZero();
        assertThat(controles.estado(sessao).status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        verify(adaptador, never()).responder(any(), any());
    }

    /** A sessão pausada nem chega a moderar: a recusa vem antes da chamada externa. */
    @Test
    void sessaoPausadaNaoModeraAPergunta() {
        controles.pausar(sessao);

        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Oi")).isInstanceOf(SessaoForaDoEstado.class);

        verify(adaptador, never()).moderar(any());
    }

    /** O lock não fica preso durante a moderação, então o estado é conferido de novo antes de gravar. */
    @Test
    void estadoEConferidoDeNovoDepoisDaModeracao() {
        when(adaptador.moderar("Primeira")).thenAnswer(chamada -> {
            controles.pausar(sessao);
            return ResultadoDaModeracao.aprovado();
        });

        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Primeira")).isInstanceOf(SessaoForaDoEstado.class);

        assertThat(contar("SELECT count(*) FROM mensagem WHERE sessao_id = ?")).isZero();
    }

    /**
     * RN-20: o professor pausou enquanto a pergunta era moderada, e ela veio sinalizada. A pergunta não
     * é gravada nem pausa de novo, mas o caso vai ao curador em vez de virar uma recusa comum.
     */
    @Test
    void perguntaSinalizadaComASessaoPausadaNaModeracaoAindaAbreOCaso() {
        when(adaptador.moderar("Pergunta imprópria")).thenAnswer(chamada -> {
            controles.pausar(sessao);
            return SINALIZADO;
        });

        assertThatThrownBy(() -> conversa.enviarPergunta(sessao, "Pergunta imprópria"))
                .isInstanceOf(PerguntaBloqueada.class);

        assertThat(contar("SELECT count(*) FROM mensagem WHERE sessao_id = ?")).isZero();
        assertThat(pausa()).isEqualTo(MotivoDaPausa.MANUAL.name());
        assertThat(contar("SELECT count(*) FROM evento_sessao WHERE sessao_id = ? AND tipo = 'PAUSA'")).isEqualTo(1);
        assertThat(casoAberto()).containsEntry("origem", "PERGUNTA").containsEntry("pergunta", "Pergunta imprópria");
    }

    /** RN-18 e RN-20: o limite foi atingido durante a moderação; a pergunta sinalizada vai ao curador. */
    @Test
    void perguntaSinalizadaNoLimiteAindaAbreOCaso() {
        jdbc.update("INSERT INTO parametro (chave, valor) VALUES (?, '1')", LIMITE);
        long outra = inicio.iniciar(dados.turma(), novoCenario()).id();
        respondeEmTrechos("Resposta.");
        when(adaptador.moderar("Pergunta imprópria")).thenAnswer(chamada -> {
            conversa.enviarPergunta(outra, "Primeira");
            conversa.responderPendente(outra, new Tela());
            return SINALIZADO;
        });

        assertThatThrownBy(() -> conversa.enviarPergunta(outra, "Pergunta imprópria"))
                .isInstanceOf(PerguntaBloqueada.class);

        assertThat(jdbc.queryForObject("SELECT motivo_pausa FROM sessao WHERE id = ?", String.class, outra))
                .isEqualTo(MotivoDaPausa.CONTEUDO_BLOQUEADO.name());
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                        "SELECT count(*) FROM caso_bloqueado WHERE sessao_id = ? AND origem = 'PERGUNTA'",
                        Integer.class,
                        outra)
                == 1);
    }

    /** RN-20: só as frases aprovadas chegam à tela, inteiras, na ordem em que o modelo as gerou. */
    @Test
    void frasesAprovadasChegamNaOrdem() {
        respondeEmTrechos("O Sr. Ferber me ", "ajudou. Voei em 1906", ". Foi incrível!", " E depois");
        var tela = new Tela();
        conversa.enviarPergunta(sessao, "Como foi?");

        conversa.responderPendente(sessao, tela);

        assertThat(tela.eventos).containsExactly("O Sr. Ferber me ajudou. ", "Voei em 1906. ", "Foi incrível! ", "E depois");
        InOrder ordem = inOrder(adaptador);
        ordem.verify(adaptador).moderar("O Sr. Ferber me ajudou.");
        ordem.verify(adaptador).moderar("Voei em 1906.");
        ordem.verify(adaptador).moderar("Foi incrível!");
        ordem.verify(adaptador).moderar("E depois");
    }

    /**
     * RN-20: a frase bloqueada não aparece, nem em parte, e as seguintes também não. A sessão já está
     * pausada quando a tela sabe do bloqueio, e o resto da resposta ainda é consumido para os tokens.
     */
    @Test
    void fraseBloqueadaNaoApareceNemAsSeguintes() {
        List<Long> metadados = respondeEmTrechos(
                "Sou Santos ", "Dumont. Uma frase impró", "pria aqui. Outra frase.", " Mais uma.");
        when(adaptador.moderar("Uma frase imprópria aqui.")).thenReturn(SINALIZADO);
        List<String> statusNoBloqueio = new ArrayList<>();
        var tela = new Tela();
        tela.aoBloquear = () -> statusNoBloqueio.add(pausa());
        conversa.enviarPergunta(sessao, "Quem é você?");

        assertThat(conversa.responderPendente(sessao, tela)).isEmpty();

        assertThat(tela.eventos).containsExactly("Sou Santos Dumont. ", BLOQUEIO);
        assertThat(String.join("", tela.eventos)).doesNotContain("impró").doesNotContain("Outra");
        assertThat(statusNoBloqueio).containsExactly(MotivoDaPausa.CONTEUDO_BLOQUEADO.name());
        verify(adaptador, never()).moderar("Outra frase.");
        assertThat(contar("SELECT count(*) FROM mensagem WHERE sessao_id = ? AND autoria = 'IA'")).isZero();
        assertThat(estados.buscar(sessao).orElseThrow().ultimasMensagens())
                .extracting(MensagemExibida::autoria)
                .containsExactly(Autoria.TURMA);
        Map<String, Object> caso = casoComMetadados();
        assertThat(caso)
                .containsEntry("origem", "RESPOSTA")
                .containsEntry("pergunta", "Quem é você?")
                .containsEntry("trecho_bloqueado", "Uma frase imprópria aqui.")
                .containsEntry("resposta_ia_meta_id", metadados.getFirst());
        assertThat(categoriasDo(caso)).containsExactly("violence");
    }

    /** RNF-13: o adaptador termina a resposta bloqueada e grava os metadados de consumo dela. */
    @Test
    void bloqueioGravaOsMetadadosDeConsumo() {
        List<String> depoisDoBloqueio = new ArrayList<>();
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept("Texto impróprio. ");
            depoisDoBloqueio.add("O modelo continua gerando.");
            aoReceber.accept("O modelo continua gerando. ");
            aoReceber.accept("Até o fim.");
            long id = jdbc.queryForObject(
                    """
                    INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt, tokens_entrada, tokens_saida)
                    VALUES ('openai', 'm', 'v1', 120, 45) RETURNING id""",
                    Long.class);
            return new RespostaDoPersonagem("Texto impróprio. O modelo continua gerando. Até o fim.", id);
        });
        when(adaptador.moderar("Texto impróprio.")).thenReturn(SINALIZADO);
        var tela = new Tela();
        conversa.enviarPergunta(sessao, "Oi");

        conversa.responderPendente(sessao, tela);

        assertThat(tela.eventos).containsExactly(BLOQUEIO);
        assertThat(depoisDoBloqueio).hasSize(1);
        Map<String, Object> caso = casoComMetadados();
        assertThat(jdbc.queryForObject(
                        "SELECT tokens_saida FROM resposta_ia_meta WHERE id = ?",
                        Integer.class,
                        caso.get("resposta_ia_meta_id")))
                .isEqualTo(45);
    }

    /**
     * O caso é aberto na mesma transação da pausa, antes de o restante da resposta terminar, e ganha os
     * metadados quando ela termina. Se a aplicação parar no meio, a sessão pausada já tem o seu caso.
     */
    @Test
    void casoAbreNaPausaEGanhaOsMetadadosNoFim() {
        List<Object> casoAntesDoFim = new ArrayList<>();
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept("Texto impróprio. ");
            casoAntesDoFim.add(casoAberto().get("resposta_ia_meta_id"));
            aoReceber.accept("Até o fim.");
            return new RespostaDoPersonagem("Texto impróprio. Até o fim.", metadados());
        });
        when(adaptador.moderar("Texto impróprio.")).thenReturn(SINALIZADO);
        conversa.enviarPergunta(sessao, "Oi");

        conversa.responderPendente(sessao, new Tela());

        assertThat(casoAntesDoFim).containsExactly((Object) null);
        assertThat(casoComMetadados().get("resposta_ia_meta_id")).isNotNull();
        assertThat(contar("SELECT count(*) FROM caso_bloqueado WHERE sessao_id = ?")).isEqualTo(1);
    }

    /**
     * Antes do bloqueio, a sessão está ocupada com a resposta. Depois dele, o restante ainda chega só
     * para os metadados, mas não prende a sessão: retomada, a pergunta seguinte é respondida na hora.
     */
    @Test
    void restoDaRespostaBloqueadaNaoPrendeASessao() {
        List<Optional<MensagemExibida>> respostaDaSeguinte = new ArrayList<>();
        AtomicInteger chamadas = new AtomicInteger();
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            if (chamadas.incrementAndGet() > 1) {
                aoReceber.accept("Voou em 1906.");
                return new RespostaDoPersonagem("Voou em 1906.", metadados());
            }
            assertThatThrownBy(() -> conversa.responderPendente(sessao, new Tela()))
                    .isInstanceOf(RespostaEmAndamento.class);
            aoReceber.accept("Texto impróprio. ");
            controles.retomar(sessao);
            conversa.enviarPergunta(sessao, "E o 14-bis?");
            respostaDaSeguinte.add(conversa.responderPendente(sessao, new Tela()));
            aoReceber.accept("Resto que só conta nos tokens.");
            return new RespostaDoPersonagem("Texto impróprio. Resto que só conta nos tokens.", metadados());
        });
        when(adaptador.moderar("Texto impróprio.")).thenReturn(SINALIZADO);
        conversa.enviarPergunta(sessao, "Oi");

        assertThat(conversa.responderPendente(sessao, new Tela())).isEmpty();

        assertThat(respostaDaSeguinte).hasSize(1);
        assertThat(respostaDaSeguinte.getFirst()).map(MensagemExibida::texto).contains("Voou em 1906.");
        assertThat(conversa.responderPendente(sessao, new Tela())).isEmpty();
    }

    /** RN-20: quem decide é o texto ter sido sinalizado, mesmo sem nenhuma categoria. */
    @Test
    void textoSinalizadoSemCategoriaTambemBloqueia() {
        respondeEmTrechos("Primeira frase. ", "Segunda frase.");
        when(adaptador.moderar("Primeira frase.")).thenReturn(new ResultadoDaModeracao(true, List.of()));
        var tela = new Tela();
        conversa.enviarPergunta(sessao, "Oi");

        conversa.responderPendente(sessao, tela);

        assertThat(tela.eventos).containsExactly(BLOQUEIO);
        assertThat(categoriasDo(casoAberto())).isEmpty();
    }

    /** A última frase, sem pontuação final, também passa pela moderação antes da tela. */
    @Test
    void ultimaFraseSemPontuacaoTambemEModerada() {
        respondeEmTrechos("Frase aprovada. ", "fim sem ponto");
        when(adaptador.moderar("fim sem ponto")).thenReturn(SINALIZADO);
        var tela = new Tela();
        conversa.enviarPergunta(sessao, "Oi");

        conversa.responderPendente(sessao, tela);

        assertThat(tela.eventos).containsExactly("Frase aprovada. ", BLOQUEIO);
        assertThat(casoAberto()).containsEntry("trecho_bloqueado", "fim sem ponto");
    }

    /**
     * Depois do bloqueio, a pergunta não espera mais resposta, mesmo com o estado refeito do banco: ao
     * retomar, o modelo não é chamado de novo e a turma faz outra pergunta.
     */
    @Test
    void perguntaComRespostaBloqueadaDeixaDeFicarPendente() {
        respondeEmTrechos("Texto impróprio.");
        when(adaptador.moderar("Texto impróprio.")).thenReturn(SINALIZADO);
        conversa.enviarPergunta(sessao, "Quem é você?");
        conversa.responderPendente(sessao, new Tela());

        controles.retomar(sessao);

        assertThat(controles.estado(sessao).perguntaPendente()).isEmpty();
        assertThat(conversa.responderPendente(sessao, new Tela())).isEmpty();
        verify(adaptador, times(1)).responder(any(), any());
        assertThat(conversa.enviarPergunta(sessao, "E o 14-bis?").pergunta().texto()).isEqualTo("E o 14-bis?");
        assertThat(jdbc.queryForList(
                        "SELECT resposta_bloqueada FROM mensagem WHERE sessao_id = ? ORDER BY id", Boolean.class, sessao))
                .containsExactly(true, false);
    }

    /** RN-20: depois de retomar, a pergunta que gerou o bloqueio não volta ao modelo no histórico. */
    @Test
    void perguntaComRespostaBloqueadaNaoVoltaAoModelo() {
        respondeEmTrechos("Sou Santos Dumont.");
        conversa.enviarPergunta(sessao, "Quem é você?");
        conversa.responderPendente(sessao, new Tela());
        when(adaptador.moderar("Sou Santos Dumont.")).thenReturn(SINALIZADO);
        conversa.enviarPergunta(sessao, "Pergunta que gerou o bloqueio");
        conversa.responderPendente(sessao, new Tela());
        controles.retomar(sessao);
        when(adaptador.moderar("Sou Santos Dumont.")).thenReturn(ResultadoDaModeracao.aprovado());
        conversa.enviarPergunta(sessao, "E o 14-bis?");

        conversa.responderPendente(sessao, new Tela());

        PedidoDeFala pedido = ultimoPedido();
        assertThat(pedido.pergunta()).isEqualTo("E o 14-bis?");
        assertThat(pedido.historico()).containsExactly(
                new MensagemDaConversa(MensagemDaConversa.Autor.TURMA, "Quem é você?"),
                new MensagemDaConversa(MensagemDaConversa.Autor.PERSONAGEM, "Sou Santos Dumont."));
    }

    /**
     * RN-19 e RN-20: a sessão foi reiniciada durante a resposta e outra pergunta entrou. O bloqueio
     * marca a pergunta que estava sendo respondida, e a nova continua esperando a resposta dela.
     */
    @Test
    void bloqueioDepoisDoReinicioMarcaAPerguntaRespondida() {
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            controles.reiniciar(sessao);
            conversa.enviarPergunta(sessao, "Depois do reinício");
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept("Texto impróprio.");
            return new RespostaDoPersonagem("Texto impróprio.", metadados());
        });
        when(adaptador.moderar("Texto impróprio.")).thenReturn(SINALIZADO);
        conversa.enviarPergunta(sessao, "Respondida");

        conversa.responderPendente(sessao, new Tela());

        assertThat(jdbc.queryForList(
                        "SELECT texto FROM mensagem WHERE sessao_id = ? AND resposta_bloqueada", String.class, sessao))
                .containsExactly("Respondida");
        controles.retomar(sessao);
        assertThat(controles.estado(sessao).perguntaPendente())
                .map(MensagemExibida::texto)
                .contains("Depois do reinício");
    }

    /** RNF-04: a moderação da resposta falhou como o provedor: nada aparece e a pergunta continua pendente. */
    @Test
    void falhaDaModeracaoDaRespostaDeixaAPerguntaPendente() {
        respondeEmTrechos("Sou Santos Dumont. ", "Voei em 1906.");
        when(adaptador.moderar("Sou Santos Dumont.")).thenThrow(new FalhaNoProvedorDeIa("fora do ar", null));
        var tela = new Tela();
        conversa.enviarPergunta(sessao, "Quem é você?");

        assertThatThrownBy(() -> conversa.responderPendente(sessao, tela)).isInstanceOf(FalhaNoProvedorDeIa.class);

        assertThat(tela.eventos).isEmpty();
        assertThat(contar("SELECT count(*) FROM mensagem WHERE sessao_id = ? AND autoria = 'IA'")).isZero();
        assertThat(controles.estado(sessao).status()).isEqualTo(StatusDaSessao.EM_ANDAMENTO);
        assertThat(controles.estado(sessao).perguntaPendente()).isPresent();
        assertThat(contar("SELECT count(*) FROM caso_bloqueado WHERE sessao_id = ?")).isZero();
    }

    /** O provedor falhou depois do bloqueio: a tela já foi avisada e o caso vai ao curador sem metadados. */
    @Test
    void falhaDoProvedorDepoisDoBloqueioAindaAbreOCaso() {
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept("Texto impróprio. ");
            aoReceber.accept("Mais texto.");
            throw new FalhaNoProvedorDeIa("caiu no meio", null);
        });
        when(adaptador.moderar("Texto impróprio.")).thenReturn(SINALIZADO);
        var tela = new Tela();
        conversa.enviarPergunta(sessao, "Oi");

        assertThat(conversa.responderPendente(sessao, tela)).isEmpty();

        assertThat(tela.eventos).containsExactly(BLOQUEIO);
        assertThat(casoAberto()).containsEntry("resposta_ia_meta_id", null);
    }

    /** A tela que fechou no aviso do bloqueio não impede a pausa nem o caso. */
    @Test
    void telaFechadaNoBloqueioNaoImpedeOCaso() {
        respondeEmTrechos("Texto impróprio.");
        when(adaptador.moderar("Texto impróprio.")).thenReturn(SINALIZADO);
        conversa.enviarPergunta(sessao, "Oi");
        var tela = new Tela();
        tela.aoBloquear = () -> {
            throw new IllegalStateException("a tela fechou");
        };

        assertThat(conversa.responderPendente(sessao, tela)).isEmpty();

        assertThat(pausa()).isEqualTo(MotivoDaPausa.CONTEUDO_BLOQUEADO.name());
        assertThat(casoAberto()).containsEntry("origem", "RESPOSTA");
    }

    /** O professor pausou durante a resposta: o bloqueio não pausa de novo, mas o caso vai ao curador. */
    @Test
    void bloqueioComASessaoJaPausadaSoAbreOCaso() {
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            controles.pausar(sessao);
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept("Texto impróprio.");
            return new RespostaDoPersonagem("Texto impróprio.", metadados());
        });
        when(adaptador.moderar("Texto impróprio.")).thenReturn(SINALIZADO);
        conversa.enviarPergunta(sessao, "Oi");

        conversa.responderPendente(sessao, new Tela());

        assertThat(pausa()).isEqualTo(MotivoDaPausa.MANUAL.name());
        assertThat(contar("SELECT count(*) FROM evento_sessao WHERE sessao_id = ? AND tipo = 'PAUSA'")).isEqualTo(1);
        assertThat(casoAberto()).containsEntry("origem", "RESPOSTA");
    }

    /** O caso aberto pela Auditoria, que recebe o evento depois da confirmação, em outra thread. */
    private Map<String, Object> casoAberto() {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> contar("SELECT count(*) FROM caso_bloqueado WHERE sessao_id = ?") == 1);
        return jdbc.queryForMap("SELECT * FROM caso_bloqueado WHERE sessao_id = ?", sessao);
    }

    /** O caso da resposta bloqueada, depois de a Auditoria receber também o evento com os metadados. */
    private Map<String, Object> casoComMetadados() {
        await().atMost(Duration.ofSeconds(10)).until(() -> contar(
                        "SELECT count(*) FROM caso_bloqueado WHERE sessao_id = ? AND resposta_ia_meta_id IS NOT NULL")
                == 1);
        return casoAberto();
    }

    private static List<String> categoriasDo(Map<String, Object> caso) {
        try {
            return List.of((String[]) ((Array) caso.get("categorias")).getArray());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private String pausa() {
        return jdbc.queryForObject("SELECT motivo_pausa FROM sessao WHERE id = ?", String.class, sessao);
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class, sessao);
    }

    /** Tela simulada: guarda as frases exibidas e marca o bloqueio, que não traz texto. */
    private static final class Tela implements TelaDaResposta {

        private final List<String> eventos = new ArrayList<>();
        private Runnable aoBloquear = () -> {};

        @Override
        public void exibir(String frase) {
            eventos.add(frase);
        }

        @Override
        public void bloquear() {
            eventos.add(BLOQUEIO);
            aoBloquear.run();
        }
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
