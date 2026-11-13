package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
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
 * Critérios de aceite da issue 24 pela tela, com requisições HTTP reais: preparação da sessão (RF-14),
 * modo projeção com o aviso de IA sempre visível (RF-15, RN-14) e os controles do painel com atalho
 * (RF-17, RNF-05). O adaptador de IA é simulado. Mesmo contexto do {@link ConversaPelaTelaTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lab.teste.contexto=conversa-pela-tela")
@Import(InfraestruturaDeTeste.class)
class ProjecaoPelaTelaTest {

    private static final String SENHA = "uma senha de teste";
    private static final String AVISO_DE_IA = "Personagem simulado por Inteligência Artificial · pode conter erros";
    private static final Pattern SESSAO = Pattern.compile("/sessoes/(\\d+)$");

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
    // Preparação (RF-14)
    // ---------------------------------------------------------------------------------------------

    @Test
    void preparacaoListaTurmasECenariosComOEscolhidoMarcado() throws Exception {
        var corpo = professora.get("/sessoes/nova?cenario=" + cenario).body();

        assertThat(corpo)
                .contains("Prepare a conversa")
                .contains("O voo do 14-bis")
                .containsPattern("value=\"" + cenario + "\"\\s+checked")
                .contains("value=\"" + turma + "\"");
    }

    @Test
    void iniciarSemEscolherVoltaComAviso() throws Exception {
        var resposta = professora.postHtmx("/sessoes", "cenario=" + cenario);

        assertThat(local(resposta)).endsWith("/sessoes/nova?cenario=" + cenario);
        assertThat(professora.get("/sessoes/nova?cenario=" + cenario).body()).contains("Escolha a turma e o cenário");
    }

    /** RN-10: uma sessão por turma; a segunda volta à preparação com o motivo. */
    @Test
    void turmaComSessaoEmAndamentoNaoComecaOutra() throws Exception {
        iniciar();

        var resposta = professora.postHtmx("/sessoes", "turma=" + turma + "&cenario=" + cenario);

        assertThat(local(resposta)).endsWith("/sessoes/nova?cenario=" + cenario);
        assertThat(professora.get("/sessoes/nova?cenario=" + cenario).body())
                .contains("Esta turma já tem uma sessão em andamento");
    }

    @Test
    void soOProfessorAbreAPreparacao() throws Exception {
        assertThat(logado(Perfil.CURADOR).get("/sessoes/nova").statusCode()).isEqualTo(403);
        assertThat(logado(Perfil.ADMINISTRADOR).get("/sessoes/nova").statusCode()).isEqualTo(403);
    }

    // ---------------------------------------------------------------------------------------------
    // Modo projeção (RF-15, RN-14, RNF-05)
    // ---------------------------------------------------------------------------------------------

    @Test
    void projecaoMostraOCenarioOAvisoDeIaEOsControlesComAtalho() throws Exception {
        long sessao = iniciar();

        var corpo = professora.get("/sessoes/" + sessao).body();

        assertThat(corpo)
                .contains("O voo do 14-bis")
                .contains("HISTÓRIA · 8º ANO · EF08HI03")
                .contains(AVISO_DE_IA)
                .contains("hx-post=\"/sessoes/" + sessao + "/perguntas\"")
                .contains("0/40 perguntas")
                .contains("data-atalho=\"p\"").contains("<kbd>P</kbd>")
                .contains("data-atalho=\"r\"").contains("data-atalho=\"e\"")
                .contains("Em andamento")
                .containsPattern("/webjars/htmx-ext-sse/[\\d.]+/sse\\.js")
                .doesNotContain("sse-connect");
    }

    /** Só o professor que iniciou a sessão a vê (RN-10). */
    @Test
    void outroProfessorNaoVeASessao() throws Exception {
        long sessao = iniciar();

        assertThat(logado(Perfil.PROFESSOR).get("/sessoes/" + sessao).statusCode()).isEqualTo(403);
    }

    /** RNF-04: a pergunta sem resposta reabre o canal quando a tela é carregada de novo. */
    @Test
    void perguntaPendenteReabreOCanalAoCarregar() throws Exception {
        long sessao = iniciar();
        professora.perguntar(sessao, "Quando foi o voo?");

        var corpo = professora.get("/sessoes/" + sessao).body();

        assertThat(corpo).contains("Quando foi o voo?").contains("sse-connect=\"/sessoes/" + sessao + "/resposta\"");
    }

    @Test
    void conversaRespondidaApareceAoCarregarEContaNoPainel() throws Exception {
        long sessao = iniciar();
        respondeCom("Foi em 1906.");
        professora.perguntar(sessao, "Quando foi o voo?");
        professora.resposta(sessao);

        var corpo = professora.get("/sessoes/" + sessao).body();

        assertThat(corpo).contains("Quando foi o voo?").contains("Foi em 1906.").contains("1/40 perguntas")
                .doesNotContain("sse-connect");
    }

    // ---------------------------------------------------------------------------------------------
    // Controles (RF-17)
    // ---------------------------------------------------------------------------------------------

    @Test
    void pausarERetomarDevolvemOPainelAtualizado() throws Exception {
        long sessao = iniciar();

        var pausada = professora.postHtmx("/sessoes/" + sessao + "/pausar", "");
        var retomada = professora.postHtmx("/sessoes/" + sessao + "/retomar", "");

        assertThat(pausada.statusCode()).isEqualTo(200);
        assertThat(pausada.body())
                .contains("id=\"painel\"").contains("Pausada").contains("Retomar")
                .contains("data-pode-perguntar=\"false\"");
        assertThat(retomada.body()).contains("Em andamento").contains("Pausar").contains("data-pode-perguntar=\"true\"");
    }

    /** A recusa vai para a área de avisos da tela, como as da pergunta. */
    @Test
    void pausarDuasVezesMostraAviso() throws Exception {
        long sessao = iniciar();
        professora.postHtmx("/sessoes/" + sessao + "/pausar", "");

        var resposta = professora.postHtmx("/sessoes/" + sessao + "/pausar", "");

        assertThat(resposta.statusCode()).isEqualTo(409);
        assertThat(resposta.headers().firstValue("HX-Retarget")).contains("#aviso-da-sessao");
        assertThat(resposta.body()).contains("A sessão já estava assim");
    }

    /** RN-19: reiniciar limpa a tela; a conversa continua registrada. */
    @Test
    void reiniciarLimpaAConversaDaTela() throws Exception {
        long sessao = iniciar();
        respondeCom("Foi em 1906.");
        professora.perguntar(sessao, "Quando foi o voo?");
        professora.resposta(sessao);

        var resposta = professora.postHtmx("/sessoes/" + sessao + "/reiniciar", "");

        assertThat(resposta.body()).contains("id=\"painel\"").contains("hx-swap-oob=\"true\"");
        assertThat(professora.get("/sessoes/" + sessao).body()).doesNotContain("Quando foi o voo?");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE sessao_id = ?", Integer.class, sessao))
                .isEqualTo(2);
    }

    @Test
    void encerrarLevaAoResumo() throws Exception {
        long sessao = iniciar();

        var resposta = professora.postHtmx("/sessoes/" + sessao + "/encerrar", "");

        assertThat(resposta.headers().firstValue("HX-Redirect")).contains("/sessoes/" + sessao + "/encerrada");
        assertThat(local(professora.get("/sessoes/" + sessao))).endsWith("/sessoes/" + sessao + "/encerrada");
        assertThat(professora.get("/sessoes/" + sessao + "/encerrada").body())
                .contains("Boa conversa!")
                .contains("O voo do 14-bis")
                .contains("Perguntas");
    }

    @Test
    void resumoDeSessaoEmAndamentoVoltaAProjecao() throws Exception {
        long sessao = iniciar();

        assertThat(local(professora.get("/sessoes/" + sessao + "/encerrada"))).endsWith("/sessoes/" + sessao);
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

    private void respondeCom(String texto) {
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept(texto);
            long metadados = jdbc.queryForObject(
                    "INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt) VALUES ('openai', 'm', 'v1') RETURNING id",
                    Long.class);
            return new RespostaDoPersonagem(texto, metadados);
        });
    }

    private Navegador logado(Perfil perfil) throws Exception {
        String email = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Profissional", email, SENHA, Set.of(perfil));
        var navegador = new Navegador(porta);
        navegador.entrar(email, SENHA);
        return navegador;
    }

    private static String local(HttpResponse<String> resposta) {
        return resposta.headers().firstValue("Location").orElse("");
    }
}
