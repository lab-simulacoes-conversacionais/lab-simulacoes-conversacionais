package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.LimiteDoTesteAtingido;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste.Situacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntasDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TesteNaoPermitido;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.CenarioDaConversa;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import br.ufg.inf.labsimulacoes.ia.MensagemDaConversa;
import br.ufg.inf.labsimulacoes.ia.MensagemDaConversa.Autor;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modo de teste do cenário pelo curador (RF-09, RN-06), com perfis e PostgreSQL reais e o adaptador de
 * IA simulado. O filtro simulado bloqueia todo texto com {@value #IMPROPRIO}. Cada teste desfaz o que
 * gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class TesteDoCenarioTest {

    private static final String AUTORA = "autora-teste@escola.test";
    private static final String CURADORA = "curadora-teste@escola.test";
    private static final String CURADOR = "curador-teste@escola.test";
    private static final String IMPROPRIO = "[impróprio]";
    /** A pergunta que faz o personagem simulado responder algo que o filtro bloqueia. */
    private static final String PROVOCACAO = "Conte uma história violenta";

    /** As tabelas que o teste não pode tocar: nada de sessão de turma, de evento ou de caso na fila. */
    private static final List<String> TABELAS_DA_SALA =
            List.of("sessao", "mensagem", "evento_sessao", "event_publication", "caso_bloqueado");

    @MockitoBean
    private AdaptadorDeIa adaptador;

    @Autowired
    private TesteDoCenario teste;

    @Autowired
    private PerguntasDeTeste perguntas;

    @Autowired
    private AutoriaDeCenarios autoria;

    @Autowired
    private EnvioACuradoria envio;

    @Autowired
    private DevolucaoDeCenario devolucao;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private long curadora;
    /** Metadados para as respostas montadas no próprio teste. */
    private long meta;
    /** Os metadados da última resposta do adaptador simulado, que grava uns novos a cada resposta. */
    private long ultimoMeta;
    private long cenario;
    private long versao;

    @BeforeEach
    void prepararCenarioEmCuradoria() {
        inserirUsuario(AUTORA);
        curadora = inserirUsuario(CURADORA);
        inserirUsuario(CURADOR);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
        meta = novoMetadado();
        when(adaptador.moderar(anyString())).thenAnswer(chamada -> ((String) chamada.getArgument(0)).contains(IMPROPRIO)
                ? new ResultadoDaModeracao(true, List.of("violence"))
                : ResultadoDaModeracao.aprovado());
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            PedidoDeFala pedido = chamada.getArgument(0);
            String texto = pedido.pergunta().equals(PROVOCACAO) ? "Era uma vez " + IMPROPRIO : "Sobre " + pedido.pergunta();
            ultimoMeta = novoMetadado();
            return new RespostaDoPersonagem(texto, ultimoMeta);
        });

        logar(AUTORA, "ROLE_PROFESSOR");
        cenario = autoria.criar(ConteudosDeTeste.completo()).id();
        versao = envio.enviar(cenario).versao().id();
        logar(CURADORA, "ROLE_CURADOR");
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void respostaAprovadaPeloFiltroEDevolvidaEGravadaNaRodada() {
        PerguntaDeTeste respondida = teste.perguntar(cenario, "  Por que o 14-bis voou?  ");

        assertThat(respondida.id()).isNotNull();
        assertThat(respondida.situacao()).isEqualTo(Situacao.CONCLUIDA);
        assertThat(respondida.pergunta()).isEqualTo("Por que o 14-bis voou?");
        assertThat(respondida.resposta()).isEqualTo("Sobre Por que o 14-bis voou?");
        assertThat(respondida.respostaIaMetaId()).isEqualTo(ultimoMeta);
        assertThat(respondida.versaoId()).isEqualTo(versao);
        assertThat(respondida.curadorId()).isEqualTo(curadora);
        assertThat(perguntas.daRodada(versao, curadora)).containsExactly(respondida);
        assertThat(perguntas.contar(versao, curadora)).isEqualTo(1);
    }

    /** O pedido usa o adaptador e o montador da sala, com o conteúdo da versão em curadoria. */
    @Test
    void pedidoLevaOCenarioDaVersaoEmCuradoria() {
        teste.perguntar(cenario, "Oi!");

        PedidoDeFala pedido = pedidoEnviado();
        var roteiro = ConteudosDeTeste.ROTEIRO;
        assertThat(pedido.cenario()).isEqualTo(new CenarioDaConversa(
                "O voo do 14-bis", roteiro.personagem(), roteiro.papelDaIa(), roteiro.contexto(), roteiro.objetivos(),
                roteiro.limitesDeAssunto(), roteiro.fontes(), "História", 8, List.of("EF08HI03"), "13 a 14 anos"));
        assertThat(pedido.pergunta()).isEqualTo("Oi!");
        assertThat(pedido.historico()).isEmpty();
    }

    /** RNF-13: o consumo do teste fica ligado à versão testada, com a finalidade própria. */
    @Test
    void pedidoLevaAFinalidadeTesteDoCuradorEAVersao() {
        teste.perguntar(cenario, "Oi!");

        assertThat(pedidoEnviado().finalidade()).isEqualTo(FinalidadeDoConsumo.TESTE_DO_CURADOR);
        assertThat(pedidoEnviado().referenciaId()).isEqualTo(versao);
    }

    /** O histórico é o da rodada da curadora, sem as falas bloqueadas, como na sala (RN-20). */
    @Test
    void historicoDaRodadaVaiNoPedidoSemAsFalasBloqueadas() {
        teste.perguntar(cenario, "Primeira");
        teste.perguntar(cenario, "Pergunta " + IMPROPRIO);
        teste.perguntar(cenario, PROVOCACAO);
        teste.perguntar(cenario, "Segunda");

        teste.perguntar(cenario, "Terceira");

        assertThat(pedidoEnviado().historico()).containsExactly(
                new MensagemDaConversa(Autor.TURMA, "Primeira"),
                new MensagemDaConversa(Autor.PERSONAGEM, "Sobre Primeira"),
                new MensagemDaConversa(Autor.TURMA, "Segunda"),
                new MensagemDaConversa(Autor.PERSONAGEM, "Sobre Segunda"));
    }

    @Test
    void historicoDeOutroCuradorNaoEntraNoPedido() {
        teste.perguntar(cenario, "Da curadora");
        logar(CURADOR, "ROLE_CURADOR");

        teste.perguntar(cenario, "Do curador");

        assertThat(pedidoEnviado().historico()).isEmpty();
    }

    /** O histórico tem o mesmo tamanho do da sala, com as falas mais recentes. */
    @Test
    void historicoGuardaSoAsFalasMaisRecentes() {
        for (int i = 1; i <= 11; i++) {
            teste.perguntar(cenario, "Pergunta " + i);
        }

        teste.perguntar(cenario, "Mais uma");

        List<MensagemDaConversa> historico = pedidoEnviado().historico();
        assertThat(historico).hasSize(TesteDoCenario.MENSAGENS_NO_HISTORICO);
        assertThat(historico.get(0)).isEqualTo(new MensagemDaConversa(Autor.TURMA, "Pergunta 2"));
    }

    /** RN-20, RN-06: a pergunta bloqueada não chega ao modelo, fica marcada e não conta. */
    @Test
    void perguntaBloqueadaNaoChegaAoModeloEFicaMarcadaSemContar() {
        PerguntaDeTeste bloqueada = teste.perguntar(cenario, "Pergunta " + IMPROPRIO);

        assertThat(bloqueada.situacao()).isEqualTo(Situacao.PERGUNTA_BLOQUEADA);
        assertThat(bloqueada.resposta()).isNull();
        assertThat(bloqueada.respostaIaMetaId()).isNull();
        verify(adaptador, never()).responder(any(), any());
        assertThat(perguntas.daRodada(versao, curadora)).containsExactly(bloqueada);
        assertThat(perguntas.contar(versao, curadora)).isZero();
    }

    /** RN-20, RN-06: a resposta bloqueada não é devolvida nem guardada, mas a pergunta conta. */
    @Test
    void respostaBloqueadaNaoEDevolvidaNemGuardadaMasConta() {
        PerguntaDeTeste bloqueada = teste.perguntar(cenario, PROVOCACAO);

        assertThat(bloqueada.situacao()).isEqualTo(Situacao.RESPOSTA_BLOQUEADA);
        assertThat(bloqueada.resposta()).isNull();
        assertThat(bloqueada.respostaIaMetaId()).isEqualTo(ultimoMeta);
        verify(adaptador).moderar("Era uma vez " + IMPROPRIO);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pergunta_de_teste WHERE resposta LIKE '%impróprio%'", Integer.class)).isZero();
        assertThat(perguntas.contar(versao, curadora)).isEqualTo(1);
    }

    /** A falha do provedor antes de responder fica como falha, sem metadados, ocupando a vaga. */
    @Test
    void falhaDoProvedorFicaComoFalhaSemMetadados() {
        doThrow(new FalhaNoProvedorDeIa("Fora do ar", null)).when(adaptador).responder(any(), any());

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?")).isInstanceOf(FalhaNoProvedorDeIa.class);

        PerguntaDeTeste falha = unicaDaRodada();
        assertThat(falha.situacao()).isEqualTo(Situacao.FALHA);
        assertThat(falha.respostaIaMetaId()).isNull();
        assertThat(perguntas.ocupadas(versao, curadora)).isEqualTo(1);
        assertThat(perguntas.contar(versao, curadora)).isZero();
    }

    @Test
    void falhaDaModeracaoDaPerguntaFicaComoFalhaSemChamarOModelo() {
        when(adaptador.moderar(anyString())).thenThrow(new FalhaNoProvedorDeIa("Fora do ar", null));

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?")).isInstanceOf(FalhaNoProvedorDeIa.class);

        verify(adaptador, never()).responder(any(), any());
        assertThat(unicaDaRodada().situacao()).isEqualTo(Situacao.FALHA);
        assertThat(perguntas.contar(versao, curadora)).isZero();
    }

    /** RNF-13: com o teto cheio, a pergunta é recusada antes de qualquer chamada ao provedor. */
    @Test
    void tetoCheioRecusaAntesDeChamarOProvedor() {
        preencher(40, Situacao.CONCLUIDA);

        assertThatThrownBy(() -> teste.perguntar(cenario, "Mais uma")).isInstanceOf(LimiteDoTesteAtingido.class);

        verify(adaptador, never()).moderar(anyString());
        verify(adaptador, never()).responder(any(), any());
    }

    /** O teto conta qualquer situação, inclusive as pendentes e as falhas; o RN-06 só as válidas. */
    @Test
    void tetoContaPendentesEFalhasQueNaoContamNoRn06() {
        preencher(20, Situacao.PENDENTE);
        preencher(20, Situacao.FALHA);

        assertThatThrownBy(() -> teste.perguntar(cenario, "Mais uma")).isInstanceOf(LimiteDoTesteAtingido.class);

        assertThat(perguntas.contar(versao, curadora)).isZero();
    }

    /** O teto é de cada curador: o de um não afeta o de outro. */
    @Test
    void tetoDeUmCuradorNaoAfetaOutro() {
        preencher(40, Situacao.CONCLUIDA);
        logar(CURADOR, "ROLE_CURADOR");

        assertThat(teste.perguntar(cenario, "Oi?").situacao()).isEqualTo(Situacao.CONCLUIDA);
    }

    /** RN-05, RN-36: a autora com o perfil Curador também não testa a própria versão. */
    @Test
    void autoraComPerfilCuradorNaoTesta() {
        logar(AUTORA, "ROLE_PROFESSOR", "ROLE_CURADOR");

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?")).isInstanceOf(TesteNaoPermitido.class);

        verify(adaptador, never()).moderar(anyString());
        assertThat(perguntasGravadas()).isZero();
    }

    @Test
    void rascunhoNaoETestado() {
        logar(AUTORA, "ROLE_PROFESSOR");
        long rascunho = autoria.criar(ConteudosDeTeste.completo()).id();
        logar(CURADORA, "ROLE_CURADOR");

        assertThatThrownBy(() -> teste.perguntar(rascunho, "Oi?"))
                .isInstanceOf(TesteNaoPermitido.class)
                .hasMessageContaining("RASCUNHO");
        verify(adaptador, never()).moderar(anyString());
    }

    @Test
    void cenarioDevolvidoNaoETestado() {
        devolucao.devolver(cenario, "Corrigir as fontes");

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?")).isInstanceOf(TesteNaoPermitido.class);
    }

    @Test
    void cenarioAprovadoNaoETestado() {
        jdbc.update("UPDATE cenario_versao SET status = 'APROVADA', aprovado_por_id = ?, aprovado_em = now() WHERE id = ?",
                curadora, versao);
        jdbc.update("UPDATE cenario SET status = 'APROVADO' WHERE id = ?", cenario);

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?"))
                .isInstanceOf(TesteNaoPermitido.class)
                .hasMessageContaining("APROVADA");
    }

    @Test
    void perguntaVaziaERecusadaAntesDeTudo() {
        assertThatThrownBy(() -> teste.perguntar(cenario, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> teste.perguntar(cenario, null)).isInstanceOf(IllegalArgumentException.class);

        verify(adaptador, never()).moderar(anyString());
    }

    @Test
    void cenarioQueNaoExisteERecusado() {
        assertThatThrownBy(() -> teste.perguntar(cenario + 1_000, "Oi?"))
                .isInstanceOf(br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado.class);
    }

    /** RN-06: a devolução fecha a rodada, e a versão reenviada começa a contar do zero. */
    @Test
    void devolucaoZeraAContagem() {
        teste.perguntar(cenario, "Primeira");
        teste.perguntar(cenario, PROVOCACAO);
        assertThat(perguntas.contar(versao, curadora)).isEqualTo(2);

        devolucao.devolver(cenario, "Corrigir as fontes");
        assertThat(perguntas.contar(versao, curadora)).isZero();

        logar(AUTORA, "ROLE_PROFESSOR");
        autoria.alterarRascunho(cenario, outroConteudo());
        assertThat(envio.enviar(cenario).versao().id()).isEqualTo(versao);
        logar(CURADORA, "ROLE_CURADOR");

        teste.perguntar(cenario, "Depois da devolução");

        assertThat(perguntas.contar(versao, curadora)).isEqualTo(1);
        assertThat(pedidoEnviado().historico()).isEmpty();
        assertThat(perguntasGravadas()).isEqualTo(3);
    }

    /**
     * A devolução durante a geração fecha também a pergunta pendente. A resposta é guardada nela, com os
     * metadados, mas fica na rodada fechada e não é devolvida como se fosse da rodada nova.
     */
    @Test
    void devolucaoDuranteARespostaGuardaAPerguntaNaRodadaFechada() {
        doAnswer(chamada -> {
            devolucao.devolver(cenario, "Corrigir as fontes");
            return new RespostaDoPersonagem("Resposta sobre o conteúdo antigo", meta);
        }).when(adaptador).responder(any(), any());

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?"))
                .isInstanceOf(TesteNaoPermitido.class)
                .hasMessageContaining("devolvida");

        assertThat(perguntasGravadas()).isEqualTo(1);
        assertThat(perguntas.ocupadas(versao, curadora)).isZero();
        assertThat(jdbc.queryForMap(
                        "SELECT situacao, resposta_ia_meta_id, devolucao_id IS NOT NULL AS fechada FROM pergunta_de_teste"
                                + " WHERE cenario_versao_id = ?", versao))
                .containsEntry("situacao", "CONCLUIDA")
                .containsEntry("resposta_ia_meta_id", meta)
                .containsEntry("fechada", true);
    }

    /** Devolvida, editada e reenviada durante a geração: mesmo id, outro conteúdo, rodada fechada. */
    @Test
    void versaoReenviadaComOutroConteudoDuranteARespostaNaoEntraNaRodadaNova() {
        doAnswer(chamada -> {
            devolucao.devolver(cenario, "Corrigir as fontes");
            logar(AUTORA, "ROLE_PROFESSOR");
            autoria.alterarRascunho(cenario, outroConteudo());
            envio.enviar(cenario);
            logar(CURADORA, "ROLE_CURADOR");
            return new RespostaDoPersonagem("Resposta sobre o conteúdo antigo", meta);
        }).when(adaptador).responder(any(), any());

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?"))
                .isInstanceOf(TesteNaoPermitido.class)
                .hasMessageContaining("devolvida");

        assertThat(perguntas.ocupadas(versao, curadora)).isZero();
        assertThat(perguntas.contar(versao, curadora)).isZero();
    }

    /**
     * Critério de aceite da #26: o teste não gera sessão de turma nem entra nas métricas, por construção.
     * Nenhum dos três desfechos toca nas tabelas da sala, no registro de eventos ou na fila do curador.
     */
    @Test
    void testeNaoGeraSessaoMensagemEventoNemCasoNaFila() {
        Map<String, Integer> antes = contagensDaSala();

        teste.perguntar(cenario, "Oi?");
        teste.perguntar(cenario, "Pergunta " + IMPROPRIO);
        teste.perguntar(cenario, PROVOCACAO);

        assertThat(contagensDaSala()).isEqualTo(antes);
        assertThat(perguntasGravadas()).isEqualTo(3);
    }

    @Test
    void professorNaoTesta() {
        logar(AUTORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void administradorNaoTesta() {
        logar(CURADOR, "ROLE_ADMINISTRADOR");

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semLoginNaoTesta() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?"))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private PedidoDeFala pedidoEnviado() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<String>> trechos = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<PedidoDeFala> pedidos = ArgumentCaptor.forClass(PedidoDeFala.class);
        verify(adaptador, org.mockito.Mockito.atLeastOnce()).responder(pedidos.capture(), trechos.capture());
        return pedidos.getValue();
    }

    private Map<String, Integer> contagensDaSala() {
        return TABELAS_DA_SALA.stream().collect(java.util.stream.Collectors.toMap(
                tabela -> tabela, tabela -> jdbc.queryForObject("SELECT count(*) FROM " + tabela, Integer.class)));
    }

    // ---------------------------------------------------------------------------------------------
    // Revisão do PR da #26
    // ---------------------------------------------------------------------------------------------

    /**
     * Devolvida e reenviada sem edição enquanto a resposta é gerada: mesma versão, mesmo conteúdo, mas a
     * rodada da pergunta foi fechada pela devolução. A resposta antiga não pode contar na rodada nova
     * (RN-06).
     */
    @Test
    void devolucaoEReenvioSemEdicaoDuranteARespostaNaoEntramNaRodadaNova() {
        doAnswer(chamada -> {
            devolucao.devolver(cenario, "Conferir as fontes");
            logar(AUTORA, "ROLE_PROFESSOR");
            envio.enviar(cenario);
            logar(CURADORA, "ROLE_CURADOR");
            return new RespostaDoPersonagem("Resposta da rodada antiga", meta);
        }).when(adaptador).responder(any(), any());

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?")).isInstanceOf(TesteNaoPermitido.class);

        assertThat(perguntas.contar(versao, curadora)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pergunta_de_teste WHERE cenario_versao_id = ? AND devolucao_id IS NULL",
                        Integer.class, versao))
                .isZero();
    }

    /**
     * RNF-13: a resposta já foi paga quando a moderação dela falha. A pergunta fica registrada como falha,
     * com os metadados ligados, ocupando a vaga do teto, sem contar no RN-06.
     */
    @Test
    void falhaDaModeracaoDepoisDaRespostaPagaFicaRegistradaEOcupaVaga() {
        when(adaptador.moderar(anyString()))
                .thenReturn(ResultadoDaModeracao.aprovado())
                .thenThrow(new FalhaNoProvedorDeIa("Fora do ar", null));

        assertThatThrownBy(() -> teste.perguntar(cenario, "Oi?")).isInstanceOf(FalhaNoProvedorDeIa.class);

        PerguntaDeTeste falha = unicaDaRodada();
        assertThat(falha.situacao()).isEqualTo(Situacao.FALHA);
        assertThat(falha.respostaIaMetaId()).as("metadados da resposta paga ligados à pergunta").isEqualTo(ultimoMeta);
        assertThat(perguntas.ocupadas(versao, curadora)).isEqualTo(1);
        assertThat(perguntas.contar(versao, curadora)).isZero();
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM resposta_ia_meta r
                        WHERE r.id = ? AND NOT EXISTS (SELECT 1 FROM pergunta_de_teste p WHERE p.resposta_ia_meta_id = r.id)""",
                        Integer.class, ultimoMeta))
                .as("resposta_ia_meta órfã")
                .isZero();
    }

    /** RNF-13: a pergunta bloqueada é uma linha como as outras e ocupa uma vaga do teto. */
    @Test
    void perguntaBloqueadaOcupaVagaDoTeto() {
        for (int i = 0; i < 40; i++) {
            teste.perguntar(cenario, "Pergunta " + i + " " + IMPROPRIO);
        }

        assertThatThrownBy(() -> teste.perguntar(cenario, "Mais uma")).isInstanceOf(LimiteDoTesteAtingido.class);
    }

    /** O mesmo limite do campo da pergunta na sala (sessoes/projecao.html, maxlength="1000"). */
    @Test
    void perguntaAcimaDoTamanhoMaximoERecusadaAntesDeTudo() {
        assertThatThrownBy(() -> teste.perguntar(cenario, "a".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000 caracteres");

        verify(adaptador, never()).moderar(anyString());
        assertThat(perguntasGravadas()).isZero();
    }

    /** Enquanto a IA responde, a pergunta está reservada: ocupa a vaga e ainda não conta no RN-06. */
    @Test
    void perguntaPendenteSoExisteEnquantoAIaResponde() {
        doAnswer(chamada -> {
            assertThat(unicaDaRodada().situacao()).isEqualTo(Situacao.PENDENTE);
            assertThat(perguntas.ocupadas(versao, curadora)).isEqualTo(1);
            assertThat(perguntas.contar(versao, curadora)).isZero();
            ultimoMeta = novoMetadado();
            return new RespostaDoPersonagem("Olá", ultimoMeta);
        }).when(adaptador).responder(any(), any());

        teste.perguntar(cenario, "Oi?");

        assertThat(unicaDaRodada().situacao()).isEqualTo(Situacao.CONCLUIDA);
        assertThat(perguntas.contar(versao, curadora)).isEqualTo(1);
    }

    private PerguntaDeTeste unicaDaRodada() {
        List<PerguntaDeTeste> rodada = perguntas.daRodada(versao, curadora);
        assertThat(rodada).hasSize(1);
        return rodada.getFirst();
    }

    /** Ocupa vagas da rodada da curadora direto pela porta, cada uma com os seus metadados. */
    private void preencher(int quantas, Situacao situacao) {
        for (int i = 0; i < quantas; i++) {
            PerguntaDeTeste reservada =
                    perguntas.reservar(PerguntaDeTeste.reservada(versao, curadora, "Pergunta " + situacao + " " + i));
            switch (situacao) {
                case PENDENTE -> { }
                case CONCLUIDA -> perguntas.concluir(reservada.concluida("Resposta", novoMetadado()));
                case PERGUNTA_BLOQUEADA -> perguntas.concluir(reservada.comPerguntaBloqueada());
                case RESPOSTA_BLOQUEADA -> perguntas.concluir(reservada.comRespostaBloqueada(novoMetadado()));
                case FALHA -> perguntas.concluir(reservada.comFalha(novoMetadado()));
            }
        }
    }

    private long novoMetadado() {
        return jdbc.queryForObject("""
                INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt)
                VALUES ('openai', 'modelo', 'personagem-v1') RETURNING id""", Long.class);
    }

    private int perguntasGravadas() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM pergunta_de_teste WHERE cenario_versao_id = ?", Integer.class, versao);
    }

    private static ConteudoDoCenario outroConteudo() {
        return new ConteudoDoCenario(
                "O voo do 14-bis em Paris", ConteudosDeTeste.ROTEIRO, ConteudosDeTeste.CURRICULO, "13 a 14 anos", 20);
    }

    private long inserirUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
    }

    private static void logar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }
}
