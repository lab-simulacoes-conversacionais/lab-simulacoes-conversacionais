package br.ufg.inf.labsimulacoes.cenarios;

import static br.ufg.inf.labsimulacoes.cenarios.Navegador.cod;
import static br.ufg.inf.labsimulacoes.cenarios.Navegador.local;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.ia.RoteiroSugerido;
import br.ufg.inf.labsimulacoes.ia.SugestaoDeCenario;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Critérios de aceite da issue 33 pela tela, com requisições HTTP reais e o adaptador de IA simulado: o
 * pedido de sugestão de cenário (RF-07), cujo rascunho mostra a marca "Sugerido por IA" (RN-04), e o modo
 * de teste do curador com o contador de perguntas (RF-09, RN-06).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InfraestruturaDeTeste.class)
class SugestaoETesteDoCenarioPelaTelaTest {

    private static final String SENHA = "uma senha de teste";
    private static final Pattern ID_NO_EDITOR = Pattern.compile("/cenarios/(\\d+)/editar");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String HABILIDADE = "EF08HI96";
    private static final String SELO_DA_IA = "class=\"badge badge-ai\"";
    private static final String IMPROPRIO = "conteúdo impróprio";

    @LocalServerPort
    private int porta;

    @MockitoBean
    private AdaptadorDeIa adaptador;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepararCatalogoEIa() {
        habilidades.gravar(List.of(new HabilidadeBncc(
                HABILIDADE, "Identificar as transformações tecnológicas do século XIX.", "EF", List.of(8), "História")));
        when(adaptador.sugerirCenario(any())).thenAnswer(chamada -> new SugestaoDeCenario(new RoteiroSugerido(
                "Santos Dumont e o voo do 14-bis",
                "Santos Dumont",
                "Conta o primeiro voo em primeira pessoa",
                "Paris, 1906",
                List.of("Compreender a invenção do avião"),
                List.of("Política atual"),
                List.of("Museu Aeroespacial"),
                List.of("O que você sentiu no primeiro voo?"),
                "13 a 14 anos",
                20), metadados()));
        when(adaptador.moderar(anyString())).thenAnswer(chamada -> ((String) chamada.getArgument(0)).contains(IMPROPRIO)
                ? new ResultadoDaModeracao(true, List.of("violence"))
                : ResultadoDaModeracao.aprovado());
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> {
            PedidoDeFala pedido = chamada.getArgument(0);
            String texto = pedido.pergunta().contains("provoca") ? "Isto é " + IMPROPRIO : "Sobre " + pedido.pergunta();
            return new RespostaDoPersonagem(texto, metadados());
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Sugestão pela IA (RF-07, RN-04)
    // ---------------------------------------------------------------------------------------------

    @Test
    void cenarioNovoTemOBotaoDeSugerirComIa() throws Exception {
        var corpo = logado(novoUsuario(Perfil.PROFESSOR)).get("/cenarios/novo").body();

        assertThat(corpo)
                .contains("Sugerir com IA")
                .contains("href=\"/cenarios/novo?sugerir=true\"")
                .contains("action=\"/cenarios/sugestao\"")
                .containsPattern("<dialog class=\"modal modal-sugestao\" id=\"sugestao\"[^>]*data-sugestao>")
                .doesNotContainPattern("<dialog[^>]*id=\"sugestao\"[^>]*open");
    }

    /** Sem JavaScript, o link abre a mesma página com o pedido aberto. */
    @Test
    void linkDeSugerirAbreOPedidoSemJavaScript() throws Exception {
        var corpo = logado(novoUsuario(Perfil.PROFESSOR)).get("/cenarios/novo?sugerir=true").body();

        assertThat(corpo).containsPattern("<dialog[^>]*id=\"sugestao\"[^>]*open=\"open\"");
    }

    /** Critério de aceite: o rascunho sugerido mostra a marca "Sugerido por IA". */
    @Test
    void sugestaoAbreORascunhoNoEditorComAMarcaDaIa() throws Exception {
        var professora = logado(novoUsuario(Perfil.PROFESSOR));

        var resposta = professora.postForm("/cenarios/sugestao", pedido("O primeiro voo do 14-bis"));

        var id = ID_NO_EDITOR.matcher(local(resposta));
        assertThat(id.find()).as("abre o rascunho no editor: " + local(resposta)).isTrue();
        var editor = professora.get("/cenarios/" + id.group(1) + "/editar").body();
        assertThat(editor)
                .contains(SELO_DA_IA).contains("Sugerido por IA")
                .contains("Santos Dumont e o voo do 14-bis")
                .contains("Rascunho sugerido pela IA. Revise tudo, principalmente os fatos")
                .doesNotContain("data-abrir-sugestao");
        assertThat(professora.get("/cenarios").body()).contains(SELO_DA_IA);
    }

    @Test
    void cenarioCriadoPeloEditorNaoTemAMarcaDaIa() throws Exception {
        var professora = logado(novoUsuario(Perfil.PROFESSOR));

        var resposta = professora.postForm("/cenarios", "titulo=" + cod("Escrito à mão"));

        var id = ID_NO_EDITOR.matcher(local(resposta));
        assertThat(id.find()).as("rascunho salvo").isTrue();
        assertThat(professora.get("/cenarios/" + id.group(1) + "/editar").body()).doesNotContain(SELO_DA_IA);
    }

    /** O pedido incompleto não chega à IA e volta aberto, com o que já foi digitado. */
    @Test
    void pedidoSemHabilidadeVoltaAbertoEPreenchidoSemChamarAIa() throws Exception {
        var professora = logado(novoUsuario(Perfil.PROFESSOR));

        var resposta = professora.postForm("/cenarios/sugestao",
                "tema=" + cod("A Revolução Industrial") + "&componente=" + cod("História") + "&ano=8");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body())
                .contains("A sugestão precisa de ao menos uma habilidade BNCC")
                .containsPattern("<dialog[^>]*id=\"sugestao\"[^>]*open=\"open\"")
                .contains("value=\"A Revolução Industrial\"")
                .containsPattern("<option value=\"8\"\\s+selected");
        verify(adaptador, never()).sugerirCenario(any());
    }

    @Test
    void falhaDaIaVoltaComAvisoEOPedidoPreenchido() throws Exception {
        doThrow(new FalhaNoProvedorDeIa("fora do ar", null)).when(adaptador).sugerirCenario(any());
        var professora = logado(novoUsuario(Perfil.PROFESSOR));

        var resposta = professora.postForm("/cenarios/sugestao", pedido("O telégrafo"));

        assertThat(resposta.body())
                .contains("A IA não conseguiu montar a sugestão agora")
                .contains("value=\"O telégrafo\"")
                .contains("value=\"" + HABILIDADE + "\"");
    }

    // ---------------------------------------------------------------------------------------------
    // Modo de teste (RF-09, RN-06)
    // ---------------------------------------------------------------------------------------------

    @Test
    void filaMostraOModoDeTesteComOContadorZerado() throws Exception {
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Contador zerado"));

        var corpo = logado(novoUsuario(Perfil.CURADOR)).get("/curadoria?cenario=" + id).body();

        assertThat(corpo)
                .contains("Modo teste")
                .contains("0/5 perguntas mínimas")
                .contains("hx-post=\"/curadoria/" + id + "/teste\"")
                .contains("0 de 40 perguntas usadas nesta rodada.")
                .containsPattern("/webjars/htmx.org/[\\d.]+/dist/htmx\\.min\\.js");
    }

    /** Critério da issue: a conversa de teste com o contador de perguntas. */
    @Test
    void perguntaDeTesteMostraARespostaEContaParaOMinimo() throws Exception {
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Pergunta respondida"));
        var curadora = logado(novoUsuario(Perfil.CURADOR));

        var resposta = curadora.postForm("/curadoria/" + id + "/teste", "texto=" + cod("Quando foi o voo?"));

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body())
                .contains("id=\"modo-teste\"")
                .contains("Quando foi o voo?")
                .contains("Sobre Quando foi o voo?")
                .contains("Navegador português (simulação)")
                .contains("1/5 perguntas mínimas")
                .contains("1 de 40 perguntas usadas nesta rodada.")
                .contains("autofocus");
        assertThat(curadora.get("/curadoria?cenario=" + id).body())
                .contains("Sobre Quando foi o voo?").contains("1/5 perguntas mínimas");
    }

    /** RN-20: a resposta bloqueada não aparece, só o aviso neutro; ela conta para o RN-06. */
    @Test
    void respostaBloqueadaMostraSoOAvisoEConta() throws Exception {
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Resposta bloqueada"));
        var curadora = logado(novoUsuario(Perfil.CURADOR));

        var corpo = curadora.postForm("/curadoria/" + id + "/teste", "texto=" + cod("Uma provocação")).body();

        assertThat(corpo)
                .contains("O filtro bloqueou a resposta, que não foi exibida.")
                .doesNotContain(IMPROPRIO)
                .contains("1/5 perguntas mínimas");
    }

    /** RN-20: a pergunta bloqueada não aparece na conversa, nem no campo, só o aviso neutro; ela não conta. */
    @Test
    void perguntaBloqueadaMostraSoOAvisoENaoConta() throws Exception {
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Pergunta bloqueada"));
        var curadora = logado(novoUsuario(Perfil.CURADOR));

        var corpo = curadora.postForm("/curadoria/" + id + "/teste", "texto=" + cod("Pergunta com " + IMPROPRIO)).body();

        assertThat(corpo)
                .contains("O filtro bloqueou esta pergunta, e ela não chegou ao personagem.")
                .doesNotContain(IMPROPRIO)
                .contains("0/5 perguntas mínimas");
        assertThat(curadora.get("/curadoria?cenario=" + id).body()).doesNotContain(IMPROPRIO);
        verify(adaptador, never()).responder(any(), any());
    }

    @Test
    void perguntaVaziaVoltaComAvisoSemChamarAIa() throws Exception {
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Pergunta vazia"));

        var corpo = logado(novoUsuario(Perfil.CURADOR)).postForm("/curadoria/" + id + "/teste", "texto=+").body();

        assertThat(corpo).contains("Digite a pergunta de teste").contains("0/5 perguntas mínimas");
        verify(adaptador, never()).responder(any(), any());
    }

    /** A pergunta que falhou não conta, e o texto volta ao campo para tentar de novo. */
    @Test
    void falhaDoProvedorAvisaEDevolveOTextoAoCampo() throws Exception {
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Falha no teste"));
        doThrow(new FalhaNoProvedorDeIa("fora do ar", null)).when(adaptador).responder(any(), any());

        var corpo = logado(novoUsuario(Perfil.CURADOR))
                .postForm("/curadoria/" + id + "/teste", "texto=" + cod("Você tinha medo?")).body();

        assertThat(corpo)
                .contains("O personagem não conseguiu responder agora")
                .contains("value=\"Você tinha medo?\"")
                .contains("O personagem não respondeu a esta pergunta.")
                .contains("0/5 perguntas mínimas")
                .contains("1 de 40 perguntas usadas nesta rodada.");
    }

    /** RN-05, RN-36: o autor vê a seção, mas não testa, nem por requisição direta. */
    @Test
    void autorComPerfilCuradorNaoTestaOProprioCenario() throws Exception {
        var autora = logado(novoUsuario(Perfil.PROFESSOR, Perfil.CURADOR));
        long id = enviado(autora, titulo("Teste da própria autora"));

        assertThat(autora.get("/curadoria?cenario=" + id).body())
                .contains("Você criou este cenário, então o teste fica com outro curador.")
                .doesNotContain("/curadoria/" + id + "/teste");
        assertThat(autora.postForm("/curadoria/" + id + "/teste", "texto=" + cod("Posso?")).body())
                .contains("Este cenário não pode ser testado agora");
        verify(adaptador, never()).responder(any(), any());
    }

    @Test
    void professorNaoTestaPorRequisicaoDireta() throws Exception {
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Professor não testa"));

        var resposta = logado(novoUsuario(Perfil.PROFESSOR)).postForm("/curadoria/" + id + "/teste", "texto=x");

        assertThat(resposta.statusCode()).isEqualTo(403);
    }

    // ---------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------

    private static String pedido(String tema) {
        return "tema=" + cod(tema) + "&componente=" + cod("História") + "&ano=8&habilidades=" + HABILIDADE;
    }

    /** O "AAA" põe o cenário no começo da fila, que é ordenada por título, mesmo com outros testes. */
    private static String titulo(String nome) {
        return "AAA " + nome + " " + SEQUENCIA.incrementAndGet();
    }

    private long enviado(Navegador autor, String titulo) throws IOException, InterruptedException {
        var resposta = autor.postForm("/cenarios", completo(titulo));
        var id = ID_NO_EDITOR.matcher(local(resposta));
        assertThat(id.find()).as("rascunho salvo").isTrue();
        long cenario = Long.parseLong(id.group(1));
        assertThat(local(autor.postForm("/cenarios/" + cenario, completo(titulo) + "&acao=enviar")))
                .endsWith("/cenarios/enviado");
        return cenario;
    }

    private static String completo(String titulo) {
        return "titulo=" + cod(titulo)
                + "&personagem=" + cod("Navegador português")
                + "&papelDaIa=" + cod("Conta a viagem em primeira pessoa")
                + "&contexto=" + cod("Lisboa, século XV")
                + "&objetivos=" + cod("Compreender as grandes navegações")
                + "&componente=" + cod("História")
                + "&ano=8"
                + "&habilidades=" + HABILIDADE
                + "&limitesDeAssunto=" + cod("Não inventar fatos")
                + "&fontes=" + cod("Livro didático da rede")
                + "&faixaEtaria=" + cod("13 a 14 anos")
                + "&duracaoPrevistaMin=15";
    }

    private long metadados() {
        return jdbc.queryForObject(
                "INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt) VALUES ('openai', 'm', 'v1') RETURNING id",
                Long.class);
    }

    private String novoUsuario(Perfil... perfis) {
        String email = "sugestao-teste" + SEQUENCIA.incrementAndGet() + "@escola.test";
        cadastro.cadastrar("Pessoa de Teste", email, SENHA, Set.of(perfis));
        return email;
    }

    private Navegador logado(String email) throws IOException, InterruptedException {
        return Navegador.logado(porta, email, SENHA);
    }
}
