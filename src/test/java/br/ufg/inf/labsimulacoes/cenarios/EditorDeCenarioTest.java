package br.ufg.inf.labsimulacoes.cenarios;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
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

/**
 * Critérios de aceite da issue 22: criação e edição de cenário com busca na BNCC (RF-05, RF-06), envio
 * à curadoria (RF-08) e a lista dos cenários do autor (RN-37, RN-07). Requisições HTTP reais, como nas
 * demais telas protegidas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InfraestruturaDeTeste.class)
class EditorDeCenarioTest {

    private static final String SENHA = "uma senha de teste";
    private static final Pattern ID_NO_EDITOR = Pattern.compile("/cenarios/(\\d+)/editar");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    /** Códigos que nenhum outro teste usa, para o cache do catálogo não trazer resultado de outro teste. */
    private static final String HABILIDADE = "EF09HI97";
    private static final String OUTRA_HABILIDADE = "EF09HI98";

    @LocalServerPort
    private int porta;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepararCatalogo() {
        habilidades.gravar(List.of(
                new HabilidadeBncc(HABILIDADE, "Discutir a máquina analítica e o trabalho de Ada Lovelace.",
                        "EF", List.of(9), "História"),
                new HabilidadeBncc(OUTRA_HABILIDADE, "Relacionar invenções a transformações sociais.",
                        "EF", List.of(9), "História")));
    }

    // ---------------------------------------------------------------------------------------------
    // Acesso
    // ---------------------------------------------------------------------------------------------

    @Test
    void professorAbreOEditorVazioComOHtmx() throws Exception {
        var resposta = logado(novoUsuario(Perfil.PROFESSOR)).get("/cenarios/novo");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body())
                .contains("Cenário sem título")
                .contains("Pronto para curadoria?")
                .contains("Habilidade BNCC")
                .contains("X-CSRF-TOKEN")
                // O locator do WebJar põe a versão na URL, e o navegador não fica com uma versão velha.
                .containsPattern("/webjars/htmx\\.org/\\d+\\.\\d+\\.\\d+/dist/htmx\\.min\\.js");
    }

    /** O HTMX vem da própria aplicação, sem CDN, e abre sem login, como o CSS. */
    @Test
    void htmxEServidoPelaAplicacao() throws Exception {
        var resposta = new Navegador(porta).get("/webjars/htmx.org/dist/htmx.min.js");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("htmx");
    }

    /** RN-37: o administrador não edita conteúdo pedagógico, nem abre o formulário vazio. */
    @Test
    void administradorNaoAbreOEditorNemALista() throws Exception {
        var navegador = logado(novoUsuario(Perfil.ADMINISTRADOR));

        assertThat(navegador.get("/cenarios/novo").statusCode()).isEqualTo(403);
        assertThat(navegador.get("/cenarios").statusCode()).isEqualTo(403);
    }

    @Test
    void inicioDoProfessorLevaAoEditorEAosSeusCenarios() throws Exception {
        var corpo = logado(novoUsuario(Perfil.PROFESSOR)).get("/").body();

        assertThat(corpo).contains("href=\"/cenarios/novo\"").contains("href=\"/cenarios\"");
    }

    // ---------------------------------------------------------------------------------------------
    // Rascunho (RF-06)
    // ---------------------------------------------------------------------------------------------

    /** O rascunho pode ficar incompleto; o editor volta com o que foi salvo e o que ainda falta. */
    @Test
    void salvaRascunhoIncompletoEVoltaAoEditorComOQueFalta() throws Exception {
        var navegador = logado(novoUsuario(Perfil.PROFESSOR));

        var resposta = navegador.postForm("/cenarios", "titulo=Ada+e+a+m%C3%A1quina&personagem=Ada+Lovelace");

        assertThat(resposta.statusCode()).isEqualTo(302);
        assertThat(local(resposta)).matches(".*/cenarios/\\d+/editar");
        assertThat(navegador.get(caminho(local(resposta))).body())
                .contains("Rascunho salvo.")
                .contains("value=\"Ada e a máquina\"")
                .contains("value=\"Ada Lovelace\"")
                .contains("Ao menos uma habilidade da BNCC")
                .contains("Contexto");
    }

    /** O título é exigido desde o rascunho; o formulário volta com o que foi digitado. */
    @Test
    void semTituloMostraErroEMantemOQueFoiDigitado() throws Exception {
        var navegador = logado(novoUsuario(Perfil.PROFESSOR));

        var resposta = navegador.postForm("/cenarios", "titulo=+&personagem=Santos+Dumont&objetivos=Primeiro%0ASegundo");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body())
                .contains("O título do cenário é obrigatório")
                .contains("value=\"Santos Dumont\"")
                .contains("Primeiro\nSegundo");
    }

    /** Critério de aceite: mensagem clara quando a BNCC informada não existe. */
    @Test
    void habilidadeForaDoCatalogoMostraOCodigo() throws Exception {
        var navegador = logado(novoUsuario(Perfil.PROFESSOR));

        var resposta = navegador.postForm("/cenarios", "titulo=Teste&habilidades=EF09HI00");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("não estão no catálogo da BNCC: EF09HI00");
    }

    @Test
    void edicaoGuardaAsAlteracoesEAsHabilidadesComADescricao() throws Exception {
        var navegador = logado(novoUsuario(Perfil.PROFESSOR));
        long id = criar(navegador, "titulo=Original");

        navegador.postForm("/cenarios/" + id,
                "titulo=Alterado&habilidades=" + HABILIDADE + "&habilidades=" + OUTRA_HABILIDADE);

        assertThat(navegador.get("/cenarios/" + id + "/editar").body())
                .contains("value=\"Alterado\"")
                .contains("value=\"" + HABILIDADE + "\"")
                .contains("Discutir a máquina analítica")
                .contains("value=\"" + OUTRA_HABILIDADE + "\"");
    }

    /** Só o autor edita: outro professor não abre nem grava o rascunho alheio. */
    @Test
    void outroProfessorNaoEditaORascunho() throws Exception {
        long id = criar(logado(novoUsuario(Perfil.PROFESSOR)), "titulo=De+outra+pessoa");
        var outro = logado(novoUsuario(Perfil.PROFESSOR));

        assertThat(outro.get("/cenarios/" + id + "/editar").statusCode()).isEqualTo(403);
        assertThat(outro.postForm("/cenarios/" + id, "titulo=Invadido").statusCode()).isEqualTo(403);
    }

    /** O curador vê o cenário de outro autor (RN-37), mas não o edita. */
    @Test
    void curadorNaoEditaRascunhoDeOutroAutor() throws Exception {
        long id = criar(logado(novoUsuario(Perfil.PROFESSOR)), "titulo=Do+professor");

        assertThat(logado(novoUsuario(Perfil.CURADOR)).get("/cenarios/" + id + "/editar").statusCode())
                .isEqualTo(403);
    }

    // ---------------------------------------------------------------------------------------------
    // Envio à curadoria (RF-08, RN-02, RN-03)
    // ---------------------------------------------------------------------------------------------

    @Test
    void enviaCenarioCompletoMostraAConfirmacaoEBloqueiaAEdicao() throws Exception {
        var navegador = logado(novoUsuario(Perfil.PROFESSOR));

        var resposta = navegador.postForm("/cenarios", completo("Completo para envio") + "&acao=enviar");

        assertThat(local(resposta)).endsWith("/cenarios/enviado");
        assertThat(navegador.get("/cenarios/enviado").body())
                .contains("Cenário enviado")
                .contains("Completo para envio")
                .contains(HABILIDADE);
        var lista = navegador.get("/cenarios").body();
        assertThat(lista).contains("Completo para envio").contains("Em curadoria").contains("Bloqueado para edição");
        long id = idPeloTitulo("Completo para envio");
        var edicao = navegador.get("/cenarios/" + id + "/editar");
        assertThat(edicao.statusCode()).isEqualTo(302);
        assertThat(navegador.get("/cenarios").body()).contains("não pode ser editado agora");
    }

    /** Critério de aceite: o envio incompleto diz o que falta, e o rascunho não se perde. */
    @Test
    void envioIncompletoSalvaORascunhoEDizOQueFalta() throws Exception {
        var navegador = logado(novoUsuario(Perfil.PROFESSOR));

        var resposta = navegador.postForm("/cenarios", "titulo=Incompleto&acao=enviar");

        assertThat(local(resposta)).matches(".*/cenarios/\\d+/editar");
        assertThat(navegador.get(caminho(local(resposta))).body())
                .contains("O rascunho foi salvo, mas ainda não pode ir à curadoria")
                .contains("ao menos uma habilidade da BNCC")
                .contains("value=\"Incompleto\"");
    }

    @Test
    void confirmacaoAbertaDiretoVoltaParaALista() throws Exception {
        var resposta = logado(novoUsuario(Perfil.PROFESSOR)).get("/cenarios/enviado");

        assertThat(resposta.statusCode()).isEqualTo(302);
        assertThat(local(resposta)).endsWith("/cenarios");
    }

    // ---------------------------------------------------------------------------------------------
    // Fragmentos do HTMX
    // ---------------------------------------------------------------------------------------------

    @Test
    void pendenciasListamOQueFaltaSemGravar() throws Exception {
        var navegador = logado(novoUsuario(Perfil.PROFESSOR));

        var incompleto = navegador.postForm("/cenarios/pendencias", "titulo=&personagem=Ada");
        var pronto = navegador.postForm("/cenarios/pendencias", completo("Pronto"));

        assertThat(incompleto.body())
                .contains("Título do cenário")
                .contains("Ao menos uma habilidade da BNCC")
                .doesNotContain(">Personagem<")
                .contains("data-ir-para=\"2\"");
        assertThat(pronto.body()).contains("Todos os itens obrigatórios foram preenchidos.");
        assertThat(navegador.get("/cenarios").body()).contains("Você ainda não criou cenários");
    }

    @Test
    void buscaNaBnccPorCodigoEPorPalavra() throws Exception {
        var navegador = logado(novoUsuario(Perfil.PROFESSOR));

        var porCodigo = navegador.get("/cenarios/bncc?bncc-busca=" + HABILIDADE.toLowerCase());
        var porPalavra = navegador.get(
                "/cenarios/bncc?bncc-busca=" + cod("analítica") + "&componente=" + cod("História") + "&ano=9");
        var semResultado = navegador.get("/cenarios/bncc?bncc-busca=" + cod("palavra que não existe"));

        assertThat(porCodigo.body()).contains("data-vincular=\"" + HABILIDADE + "\"").contains("9º ano");
        assertThat(porPalavra.body()).contains(HABILIDADE).doesNotContain(OUTRA_HABILIDADE);
        assertThat(semResultado.body()).contains("Nenhuma habilidade encontrada");
    }

    // ---------------------------------------------------------------------------------------------
    // Meus cenários (RN-37, RN-07)
    // ---------------------------------------------------------------------------------------------

    @Test
    void listaTrazSoOsCenariosDoAutor() throws Exception {
        var autora = logado(novoUsuario(Perfil.PROFESSOR));
        criar(autora, "titulo=Meu+rascunho");
        criar(logado(novoUsuario(Perfil.PROFESSOR)), "titulo=Rascunho+alheio");

        var corpo = autora.get("/cenarios").body();

        assertThat(corpo).contains("Meu rascunho").contains("Continuar edição").doesNotContain("Rascunho alheio");
    }

    // ---------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------

    private static String completo(String titulo) {
        return "titulo=" + cod(titulo)
                + "&personagem=" + cod("Ada Lovelace")
                + "&papelDaIa=" + cod("Matemática do século XIX em primeira pessoa")
                + "&contexto=" + cod("Conversa sobre a Máquina Analítica")
                + "&objetivos=" + cod("Relacionar ciência e imaginação")
                + "&componente=" + cod("História")
                + "&ano=9"
                + "&habilidades=" + HABILIDADE
                + "&limitesDeAssunto=" + cod("Não prever tecnologias atuais")
                + "&fontes=" + cod("Science Museum")
                + "&faixaEtaria=" + cod("13 a 15 anos")
                + "&duracaoPrevistaMin=20"
                + "&perguntasDisparadoras=" + cod("O que era a Máquina Analítica?");
    }

    private long criar(Navegador navegador, String corpo) throws IOException, InterruptedException {
        var resposta = navegador.postForm("/cenarios", corpo);
        var id = ID_NO_EDITOR.matcher(local(resposta));
        assertThat(id.find()).as("redirecionamento para o editor").isTrue();
        return Long.parseLong(id.group(1));
    }

    /** Cenário bloqueado não tem link de edição na lista; o id vem do banco, pelo título único. */
    private long idPeloTitulo(String titulo) {
        return jdbc.queryForObject(
                "SELECT cenario_id FROM cenario_versao WHERE titulo = ?", Long.class, titulo);
    }

    private String novoUsuario(Perfil perfil) {
        String email = perfil.name().toLowerCase() + ".cenario" + SEQUENCIA.incrementAndGet() + "@escola.test";
        cadastro.cadastrar("Pessoa de Teste", email, SENHA, Set.of(perfil));
        return email;
    }

    private Navegador logado(String email) throws IOException, InterruptedException {
        return Navegador.logado(porta, email, SENHA);
    }

    private static String local(HttpResponse<String> resposta) {
        return Navegador.local(resposta);
    }

    private String caminho(String endereco) {
        return URI.create(endereco).getPath();
    }

    private static String cod(String valor) {
        return Navegador.cod(valor);
    }
}
