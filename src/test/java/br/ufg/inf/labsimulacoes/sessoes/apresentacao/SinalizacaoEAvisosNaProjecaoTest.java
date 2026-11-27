package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Critérios de aceite da issue 43 pela tela, com requisições HTTP reais: o botão de sinalizar a resposta
 * no modo projeção, com tipo, gravidade e justificativa (RF-23, RN-21), e os avisos que a turma não deve
 * ver só no painel do professor (RF-19, RF-20, RF-21, RN-20). O adaptador de IA é simulado. Mesmo
 * contexto do {@link ProjecaoPelaTelaTest}. Textos inventados.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lab.teste.contexto=conversa-pela-tela")
@Import(InfraestruturaDeTeste.class)
class SinalizacaoEAvisosNaProjecaoTest {

    private static final String SENHA = "uma senha de teste";
    private static final Pattern SESSAO = Pattern.compile("/sessoes/(\\d+)$");
    private static final Pattern MENSAGEM = Pattern.compile("name=\"mensagemId\" value=\"(\\d+)\"");
    private static final String RESPOSTA = "Voei em 1901, em Paris.";
    private static final String JUSTIFICATIVA = "O voo homologado foi em 23 de outubro de 1906.";

    @LocalServerPort
    private int porta;

    @MockitoBean
    private AdaptadorDeIa adaptador;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private Navegador professora;
    private long turma;
    private long cenario;

    @BeforeEach
    void entrar() throws Exception {
        dados = new DadosDeSessao(jdbc);
        turma = dados.turma();
        cenario = dados.cenarioAprovado(dados.idDe(dados.usuario("PROFESSOR")), dados.idDe(dados.usuario("CURADOR")))
                .cenarioId();
        professora = logado(Perfil.PROFESSOR);
        when(adaptador.moderar(anyString())).thenReturn(ResultadoDaModeracao.aprovado());
    }

    // ---------------------------------------------------------------------------------------------
    // Sinalizar a resposta durante a aula (RF-23)
    // ---------------------------------------------------------------------------------------------

    /** Como no protótipo: o botão amarelo no painel, com o atalho S, e o modal fora da área projetada. */
    @Test
    void painelTemOBotaoDeSinalizarComAtalho() throws Exception {
        long sessao = iniciar();

        var corpo = professora.get("/sessoes/" + sessao).body();

        assertThat(painelDe(corpo))
                .contains("data-atalho=\"s\"").contains("<kbd>S</kbd>").contains("Sinalizar erro")
                .contains("hx-get=\"/sessoes/" + sessao + "/sinalizacao\"")
                .contains("btn btn-yellow");
        assertThat(corpo).contains("<dialog class=\"modal modal-sinalizacao\" id=\"sinalizacao\"")
                .contains("Sinalizar erro na resposta");
        assertThat(areaProjetadaDe(corpo)).doesNotContain("Sinalizar erro");
    }

    /** O painel pausado também deixa sinalizar: a sinalização vale durante toda a sessão (RN-21). */
    @Test
    void sessaoPausadaContinuaComOBotaoDeSinalizar() throws Exception {
        long sessao = iniciar();

        var pausada = professora.postHtmx("/sessoes/" + sessao + "/pausar", "").body();

        assertThat(pausada).containsPattern("data-atalho=\"s\"(?![^>]*disabled)[^>]*>");
    }

    @Test
    void semRespostaOModalDizQueNaoHaOQueSinalizar() throws Exception {
        long sessao = iniciar();

        var modal = professora.get("/sessoes/" + sessao + "/sinalizacao");

        assertThat(modal.statusCode()).isEqualTo(200);
        assertThat(modal.body()).contains("O personagem ainda não respondeu nesta sessão.")
                .doesNotContain("name=\"tipo\"").contains("Fechar");
    }

    /** O modal mostra a última resposta, o tipo para escolher e a gravidade em Moderada, como no protótipo. */
    @Test
    void modalTrazOFormularioDaUltimaResposta() throws Exception {
        long sessao = sessaoComResposta();

        var modal = professora.get("/sessoes/" + sessao + "/sinalizacao").body();

        assertThat(modal)
                .contains("Sua sinalização ajuda a equipe pedagógica a revisar o cenário.")
                .contains("Última resposta do personagem").contains(RESPOSTA)
                .contains("hx-post=\"/sessoes/" + sessao + "/sinalizacao\"")
                .containsPattern("<option value=\"\" disabled[^>]*selected[^>]*>Selecione o tipo</option>")
                .contains("Erro histórico", "Erro científico", "Anacronismo", "Saída do personagem", "Conteúdo inadequado")
                .containsPattern("value=\"MODERADA\"[^>]*selected")
                .contains("Erro sobre o conceito central")
                .contains("maxlength=\"500\"").contains("Descreva brevemente o que precisa ser revisto…")
                .contains("Cancelar").contains("Enviar sinalização");
        assertThat(mensagemDe(modal)).isEqualTo(ultimaRespostaDa(sessao));
    }

    /**
     * A resposta só é gravada quando termina de chegar. Enquanto a última pergunta espera, o modal não
     * oferece a resposta anterior, que seria sinalizada por engano no lugar da que está na tela.
     */
    @Test
    void enquantoARespostaChegaOModalNaoOfereceAAnterior() throws Exception {
        long sessao = sessaoComResposta();
        professora.perguntar(sessao, "E depois, o que aconteceu?");

        var modal = professora.get("/sessoes/" + sessao + "/sinalizacao").body();

        assertThat(modal)
                .contains("A resposta à última pergunta ainda não chegou por inteiro.")
                .doesNotContain(RESPOSTA)
                .doesNotContain("name=\"mensagemId\"")
                .contains("Fechar");
    }

    /** A pergunta com a resposta bloqueada não espera resposta: o modal volta a oferecer a anterior (RN-20). */
    @Test
    void depoisDoBloqueioOModalOfereceAUltimaRespostaGravada() throws Exception {
        long sessao = sessaoComResposta();
        respondeCom("Frase imprópria aqui.");
        when(adaptador.moderar("Frase imprópria aqui.")).thenReturn(new ResultadoDaModeracao(true, List.of("hate")));
        professora.perguntar(sessao, "Quem é você?");
        professora.resposta(sessao);

        var modal = professora.get("/sessoes/" + sessao + "/sinalizacao").body();

        assertThat(modal).contains(RESPOSTA).doesNotContain("imprópria").doesNotContain("ainda não chegou");
    }

    @Test
    void sinalizarPeloModalGravaEMostraAConfirmacao() throws Exception {
        long sessao = sessaoComResposta();
        long resposta = ultimaRespostaDa(sessao);

        var envio = professora.postHtmx("/sessoes/" + sessao + "/sinalizacao",
                formulario(resposta, "ERRO_HISTORICO", "GRAVE", JUSTIFICATIVA));

        assertThat(envio.statusCode()).isEqualTo(200);
        assertThat(envio.body()).contains("Sinalização enviada. A curadoria vai analisar a resposta.")
                .containsPattern("<p class=\"sinalizacao-feita\" role=\"status\" tabindex=\"-1\" autofocus")
                .doesNotContain("name=\"tipo\"").contains("Fechar");
        assertThat(jdbc.queryForMap(
                        "SELECT perfil, tipo, gravidade_percebida, justificativa FROM sinalizacao WHERE mensagem_id = ?",
                        resposta))
                .containsEntry("perfil", "PROFESSOR")
                .containsEntry("tipo", "ERRO_HISTORICO")
                .containsEntry("gravidade_percebida", "GRAVE")
                .containsEntry("justificativa", JUSTIFICATIVA);
        assertThat(professora.get("/sessoes/" + sessao + "/sinalizacao").body())
                .contains("Você já sinalizou a última resposta do personagem.").doesNotContain("name=\"tipo\"");
    }

    /** A recusa volta no modal, com o formulário preenchido e a resposta que está sendo sinalizada. */
    @Test
    void recusaVoltaNoModalComOFormularioPreenchido() throws Exception {
        long sessao = sessaoComResposta();
        long resposta = ultimaRespostaDa(sessao);
        String caminho = "/sessoes/" + sessao + "/sinalizacao";

        var semTipo = professora.postHtmx(caminho, formulario(resposta, null, "LEVE", JUSTIFICATIVA)).body();
        var comEmail = professora.postHtmx(caminho, formulario(resposta, "ANACRONISMO", "LEVE", "Escrevam para aluno@escola.test"))
                .body();

        assertThat(semTipo).contains("Escolha o tipo do erro").contains(RESPOSTA).contains(JUSTIFICATIVA)
                .containsPattern("value=\"LEVE\"[^>]*selected")
                .containsPattern("<p class=\"field-error\" role=\"alert\" tabindex=\"-1\" autofocus");
        assertThat(comEmail).contains("A justificativa parece ter um dado pessoal (e-mail).")
                .doesNotContain("aluno@escola.test")
                .containsPattern("value=\"ANACRONISMO\"[^>]*selected");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sinalizacao WHERE mensagem_id = ?", Long.class, resposta))
                .isZero();
    }

    /** RN-37: a sessão de outro professor é como uma que não existe, e a resposta não aparece. */
    @Test
    void outroProfessorNaoVeNemSinalizaPeloModal() throws Exception {
        long sessao = sessaoComResposta();
        long resposta = ultimaRespostaDa(sessao);
        var outro = logado(Perfil.PROFESSOR);

        var modal = outro.get("/sessoes/" + sessao + "/sinalizacao").body();
        var envio = outro.postHtmx("/sessoes/" + sessao + "/sinalizacao",
                formulario(resposta, "ERRO_HISTORICO", "LEVE", JUSTIFICATIVA)).body();

        assertThat(modal).contains("Sessão não encontrada.").doesNotContain(RESPOSTA).doesNotContain("name=\"tipo\"");
        assertThat(envio).contains("Resposta não encontrada.").doesNotContain(RESPOSTA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sinalizacao WHERE mensagem_id = ?", Long.class, resposta))
                .isZero();
    }

    @Test
    void envioSemRespostaEscolhidaERecusado() throws Exception {
        long sessao = sessaoComResposta();

        var envio = professora.postHtmx("/sessoes/" + sessao + "/sinalizacao",
                "tipo=ERRO_HISTORICO&gravidade=LEVE&justificativa=" + codificar(JUSTIFICATIVA));

        assertThat(envio.statusCode()).isEqualTo(200);
        assertThat(envio.body()).contains("Resposta não encontrada.");
    }

    // ---------------------------------------------------------------------------------------------
    // Avisos só no painel do professor
    // ---------------------------------------------------------------------------------------------

    /**
     * A área de avisos das recusas e da falha fica no painel, que o professor oculta ao projetar, e
     * sobrevive às atualizações do painel. A conversa projetada não tem avisos ao professor.
     */
    @Test
    void areaDeAvisosFicaNoPainelEForaDaAreaProjetada() throws Exception {
        long sessao = iniciar();

        var corpo = professora.get("/sessoes/" + sessao).body();

        assertThat(painelDe(corpo)).contains("id=\"aviso-da-sessao\"").contains("hx-preserve");
        assertThat(areaProjetadaDe(corpo)).doesNotContain("aviso-da-sessao");
        assertThat(professora.get("/sessoes/" + sessao + "/painel").body())
                .contains("id=\"aviso-da-sessao\"").contains("hx-preserve");
    }

    /** Os avisos neutros que a tela põe na conversa projetada não falam do motivo técnico. */
    @Test
    void projecaoTrazOsAvisosNeutrosDaConversa() throws Exception {
        long sessao = iniciar();

        var corpo = professora.get("/sessoes/" + sessao).body();

        assertThat(corpo)
                .containsPattern("<template data-modelo=\"bloqueio\"><div class=\"filtered-alert\"[^>]*>")
                .contains("Resposta não exibida")
                .containsPattern("<template data-modelo=\"falha\">")
                .contains("Resposta não recebida")
                .containsPattern("<template data-modelo=\"dado-pessoal\">")
                .contains("Pergunta não enviada");
    }

    /**
     * RN-20: depois do bloqueio, a conversa projetada mostra só o aviso neutro, e o painel explica a pausa
     * automática ao professor. O texto bloqueado não aparece em lugar nenhum.
     */
    @Test
    void respostaBloqueadaMostraAvisoNeutroNaConversaEAPausaNoPainel() throws Exception {
        long sessao = iniciar();
        respondeCom("Frase imprópria aqui.");
        when(adaptador.moderar("Frase imprópria aqui.")).thenReturn(new ResultadoDaModeracao(true, List.of("hate")));
        professora.perguntar(sessao, "Quem é você?");
        professora.resposta(sessao);

        var corpo = professora.get("/sessoes/" + sessao).body();

        assertThat(areaProjetadaDe(corpo))
                .contains("Quem é você?")
                .contains("Resposta não exibida")
                .doesNotContain("encaminhado à curadoria")
                .doesNotContain("imprópria");
        assertThat(painelDe(corpo))
                .contains("Pausada")
                .contains("A sessão foi pausada porque um conteúdo foi bloqueado pelo filtro.");
    }

    // ---------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------

    private long iniciar() throws Exception {
        var resposta = professora.postHtmx("/sessoes", "turma=" + turma + "&cenario=" + cenario);
        var id = SESSAO.matcher(local(resposta));
        assertThat(id.find()).as("redireciona para a projeção: " + local(resposta)).isTrue();
        return Long.parseLong(id.group(1));
    }

    private long sessaoComResposta() throws Exception {
        long sessao = iniciar();
        respondeCom(RESPOSTA);
        professora.perguntar(sessao, "Quando foi o primeiro voo?");
        professora.resposta(sessao);
        return sessao;
    }

    private long ultimaRespostaDa(long sessao) {
        return jdbc.queryForObject(
                "SELECT max(id) FROM mensagem WHERE sessao_id = ? AND autoria = 'IA'", Long.class, sessao);
    }

    private static long mensagemDe(String modal) {
        Matcher id = MENSAGEM.matcher(modal);
        assertThat(id.find()).as("o modal leva a resposta escolhida").isTrue();
        return Long.parseLong(id.group(1));
    }

    /** Com doAnswer, dá para trocar a resposta no meio do teste sem chamar a anterior. */
    private void respondeCom(String texto) {
        doAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept(texto);
            long metadados = jdbc.queryForObject(
                    "INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt) VALUES ('openai', 'm', 'v1') RETURNING id",
                    Long.class);
            return new RespostaDoPersonagem(texto, metadados);
        }).when(adaptador).responder(any(), any());
    }

    private Navegador logado(Perfil perfil) throws Exception {
        String email = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Profissional", email, SENHA, Set.of(perfil));
        var navegador = new Navegador(porta);
        navegador.entrar(email, SENHA);
        return navegador;
    }

    private static String formulario(long mensagemId, String tipo, String gravidade, String justificativa) {
        StringBuilder corpo = new StringBuilder("mensagemId=").append(mensagemId)
                .append("&justificativa=").append(codificar(justificativa))
                .append("&gravidade=").append(codificar(gravidade));
        if (tipo != null) {
            corpo.append("&tipo=").append(codificar(tipo));
        }
        return corpo.toString();
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private static String local(HttpResponse<String> resposta) {
        return resposta.headers().firstValue("Location").orElse("");
    }

    /** A área projetada para a turma: da abertura da seção até onde começa o painel do professor. */
    private static String areaProjetadaDe(String corpo) {
        return trecho(corpo, "<section class=\"projected-area\">", "<aside id=\"painel\"");
    }

    private static String painelDe(String corpo) {
        return trecho(corpo, "<aside id=\"painel\"", "</aside>");
    }

    private static String trecho(String corpo, String inicio, String fim) {
        int de = corpo.indexOf(inicio);
        assertThat(de).as("a página tem " + inicio).isNotNegative();
        int ate = corpo.indexOf(fim, de + inicio.length());
        assertThat(ate).as("a página tem " + fim + " depois de " + inicio).isNotNegative();
        return corpo.substring(de, ate);
    }
}
