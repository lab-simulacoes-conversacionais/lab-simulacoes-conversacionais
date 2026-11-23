package br.ufg.inf.labsimulacoes.cenarios;

import static br.ufg.inf.labsimulacoes.cenarios.Navegador.cod;
import static br.ufg.inf.labsimulacoes.cenarios.Navegador.local;
import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
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

/**
 * Critérios de aceite da issue 34, pelas telas: checklist de revisão na fila (RF-10), histórico e
 * comparação lado a lado de versões com as diferenças destacadas (RF-11), filtros da biblioteca (RF-12) e
 * arquivamento pelo detalhe (RF-13). Requisições HTTP reais.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InfraestruturaDeTeste.class)
class ChecklistVersoesEFiltrosPelaTelaTest {

    private static final String SENHA = "uma senha de teste";
    private static final Pattern ID_NO_EDITOR = Pattern.compile("/cenarios/(\\d+)/editar");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String HABILIDADE = "EF07HI96";
    private static final String CONTEXTO = "Lisboa, século XV";

    private static final String CHECKLIST_COMPLETO = "itens=FATOS_CONFERIDOS&itens=LINGUAGEM_ADEQUADA"
            + "&itens=OBJETIVOS_COERENTES&itens=LIMITES_RESPEITADOS";

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private Habilidades habilidades;

    @BeforeEach
    void prepararCatalogo() {
        habilidades.gravar(List.of(new HabilidadeBncc(
                HABILIDADE, "Explicar a expansão marítima.", "EF", List.of(7), "História")));
    }

    // ---------------------------------------------------------------------------------------------
    // Checklist de revisão (RF-10, RN-06)
    // ---------------------------------------------------------------------------------------------

    @Test
    void filaMostraOChecklistDoPrototipoEOQueFaltaParaAprovar() throws Exception {
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Checklist"), CONTEXTO);
        String email = novoUsuario(Perfil.CURADOR);
        var curador = logado(email);

        assertThat(curador.get("/curadoria?cenario=" + id).body())
                .contains("Checklist de revisão")
                .contains("class=\"card review-checklist\"")
                .contains("Fatos conferidos com as fontes")
                .contains("data-aprovar")
                .contains("Faltam 5 perguntas de teste.");

        TesteDoPersonagemFeito.pelo(jdbc, email, id);
        assertThat(curador.get("/curadoria?cenario=" + id).body()).contains("Conclua todo o checklist.");
    }

    @Test
    void autorComPerfilCuradorVeOAvisoDeConflitoSemOChecklist() throws Exception {
        var autora = logado(novoUsuario(Perfil.PROFESSOR, Perfil.CURADOR));
        long id = enviado(autora, titulo("Conflito"), CONTEXTO);

        assertThat(autora.get("/curadoria?cenario=" + id).body())
                .contains("Aprovação bloqueada por conflito de autoria.")
                .doesNotContain("data-item-do-checklist")
                .contains("Histórico de versões");
    }

    // ---------------------------------------------------------------------------------------------
    // Histórico e comparação de versões (RF-11, RN-08)
    // ---------------------------------------------------------------------------------------------

    @Test
    void cenarioDeUmaVersaoNaoTemComparacao() throws Exception {
        var autora = logado(novoUsuario(Perfil.PROFESSOR));
        long id = enviado(autora, titulo("Uma versão"), CONTEXTO);

        assertThat(logado(novoUsuario(Perfil.CURADOR)).get("/curadoria?cenario=" + id).body())
                .contains("Histórico de versões")
                .contains("Primeira versão deste cenário: não há outra para comparar.");
        assertThat(autora.get("/cenarios/" + id + "/versoes").body())
                .contains("Este cenário tem uma versão só.")
                .doesNotContain("Criar versão nova");
    }

    /** Critério de aceite: a fila compara a versão em curadoria com a que está em sala, com destaque. */
    @Test
    void versaoNovaApareceNaFilaLadoALadoComADiferencaDestacada() throws Exception {
        var autora = logado(novoUsuario(Perfil.PROFESSOR));
        String titulo = titulo("Duas versões");
        long id = aprovado(autora, titulo);

        var resposta = autora.postForm("/cenarios/" + id + "/versoes/nova", "");
        assertThat(local(resposta)).endsWith("/cenarios/" + id + "/editar");
        assertThat(autora.get("/cenarios/" + id + "/editar").body()).contains("Versão 2 criada em rascunho");
        reenviar(autora, id, titulo, "Porto, século XVI");

        var fila = logado(novoUsuario(Perfil.CURADOR)).get("/curadoria?cenario=" + id).body();
        assertThat(fila)
                .contains("v1 · Antes")
                .contains("v2 · Depois")
                .contains("<span class=\"sr-only\">removido: </span>" + CONTEXTO + "</mark>")
                .contains("<span class=\"sr-only\">incluído: </span>Porto, século XVI</mark>")
                .contains("13 campos sem mudança")
                .contains("/cenarios/" + id + "/versoes");
    }

    @Test
    void autorEscolheAsVersoesComparadasERestauraAAnterior() throws Exception {
        var autora = logado(novoUsuario(Perfil.PROFESSOR));
        String titulo = titulo("Restaurada");
        long id = aprovado(autora, titulo);
        autora.postForm("/cenarios/" + id + "/versoes/nova", "");
        autora.get("/cenarios/" + id + "/editar"); // consome o aviso da versão nova
        reenviar(autora, id, titulo, "Porto, século XVI");
        aprovarComOutroCurador(id);

        var versoes = autora.get("/cenarios/" + id + "/versoes").body();
        assertThat(versoes)
                .contains("Vigente")
                .contains("Criar versão nova")
                .contains("/cenarios/" + id + "/versoes/1/restaurar")
                .doesNotContain("/cenarios/" + id + "/versoes/2/restaurar");
        assertThat(autora.get("/cenarios/" + id + "/versoes?de=2&para=2").body())
                .contains("Escolha duas versões diferentes para comparar.");
        assertThat(autora.get("/cenarios/" + id + "/versoes?de=1&para=9").body())
                .contains("Este cenário não tem a versão pedida.")
                .contains("v1 · Antes");

        var restaurada = autora.postForm("/cenarios/" + id + "/versoes/1/restaurar", "");

        assertThat(local(restaurada)).endsWith("/cenarios/" + id + "/editar");
        assertThat(autora.get("/cenarios/" + id + "/editar").body())
                .contains("A versão 1 voltou como versão 3, em rascunho.")
                .contains(CONTEXTO);
        // A versão 3 é rascunho: nada de outra versão nova nem de restaurar até ela ser decidida.
        assertThat(autora.get("/cenarios/" + id + "/versoes").body())
                .contains("v3")
                .doesNotContain("Criar versão nova")
                .doesNotContain("/restaurar");
    }

    /** RN-37: o professor só vê o histórico dos próprios cenários; o curador vê todos, sem criar versão. */
    @Test
    void historicoSegueQuemPodeVerOCenario() throws Exception {
        var autora = logado(novoUsuario(Perfil.PROFESSOR));
        long id = aprovado(autora, titulo("Acesso"));

        assertThat(logado(novoUsuario(Perfil.PROFESSOR)).get("/cenarios/" + id + "/versoes").statusCode())
                .isEqualTo(403);
        var curador = logado(novoUsuario(Perfil.CURADOR));
        assertThat(curador.get("/cenarios/" + id + "/versoes").body())
                .contains("Histórico de versões")
                .doesNotContain("Criar versão nova")
                .contains("href=\"/biblioteca/" + id + "\"");
        assertThat(curador.postForm("/cenarios/" + id + "/versoes/nova", "").statusCode()).isEqualTo(403);
        assertThat(autora.get("/cenarios").body()).contains("/cenarios/" + id + "/versoes");
    }

    // ---------------------------------------------------------------------------------------------
    // Filtros da biblioteca (RF-12)
    // ---------------------------------------------------------------------------------------------

    @Test
    void bibliotecaFiltraPorTemaComponenteAnoEHabilidade() throws Exception {
        String marca = "Marcador" + SEQUENCIA.incrementAndGet();
        long id = aprovado(logado(novoUsuario(Perfil.PROFESSOR)), titulo(marca));
        var professor = logado(novoUsuario(Perfil.PROFESSOR));

        var encontrado = professor.get("/biblioteca?busca=" + cod(marca.toLowerCase())
                + "&componente=" + cod("História") + "&ano=7&habilidade=" + HABILIDADE.toLowerCase()).body();
        assertThat(encontrado)
                .contains("/biblioteca/" + id + "\"")
                .contains("1 cenário encontrado")
                .contains("Limpar filtros")
                .containsPattern("<option value=\"7\" selected=\"selected\">7º ano</option>")
                .contains("<option value=\"" + HABILIDADE + "\">");

        assertThat(professor.get("/biblioteca?busca=" + cod(marca) + "&ano=8").body())
                .doesNotContain("/biblioteca/" + id + "\"")
                .contains("Nenhum cenário encontrado")
                .contains("Tente remover filtros ou buscar outro termo.");
        assertThat(professor.get("/biblioteca?ano=12").body()).contains("O ano 12 precisa estar entre 1 e 9.");
        assertThat(professor.get("/biblioteca").body()).doesNotContain("Limpar filtros");
    }

    /** A paginação leva a busca como foi digitada: o "+" não pode virar espaço na página seguinte. */
    @Test
    void paginacaoMantemABuscaComCaracteresEspeciais() throws Exception {
        var professor = logado(novoUsuario(Perfil.PROFESSOR));

        var pagina = professor.get("/biblioteca?pagina=1&busca=" + cod("C++ a&b") + "&ano=7").body();

        assertThat(pagina).contains("href=\"/biblioteca?pagina=0&amp;busca=C%2B%2B%20a%26b&amp;ano=7\"");
        assertThat(pagina).contains("value=\"C++ a&amp;b\"");
    }

    // ---------------------------------------------------------------------------------------------
    // Arquivamento pelo detalhe (RF-13, RN-09)
    // ---------------------------------------------------------------------------------------------

    @Test
    void curadorArquivaPeloDetalheEVoltaABiblioteca() throws Exception {
        long id = aprovado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Arquivar"));
        var professor = logado(novoUsuario(Perfil.PROFESSOR));
        assertThat(professor.get("/biblioteca/" + id).body())
                .doesNotContain("Arquivar cenário")
                .doesNotContain("/arquivar");
        var curador = logado(novoUsuario(Perfil.CURADOR));
        assertThat(curador.get("/biblioteca/" + id).body())
                .contains("Arquivar cenário")
                .contains("action=\"/curadoria/" + id + "/arquivar\"")
                .contains("href=\"/cenarios/" + id + "/versoes\"");

        var resposta = curador.postForm("/curadoria/" + id + "/arquivar", "");

        assertThat(local(resposta)).endsWith("/biblioteca");
        assertThat(curador.get("/biblioteca").body())
                .contains("foi arquivado e saiu da biblioteca")
                .doesNotContain("/biblioteca/" + id + "\"");
    }

    // ---------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------

    private static String titulo(String nome) {
        return "AAB " + nome + " " + SEQUENCIA.incrementAndGet();
    }

    /** Envia, faz o teste do personagem e aprova com o checklist inteiro, por outro curador. */
    private long aprovado(Navegador autor, String titulo) throws IOException, InterruptedException {
        long id = enviado(autor, titulo, CONTEXTO);
        aprovarComOutroCurador(id);
        return id;
    }

    private void aprovarComOutroCurador(long id) throws IOException, InterruptedException {
        String email = novoUsuario(Perfil.CURADOR);
        TesteDoPersonagemFeito.pelo(jdbc, email, id);
        assertThat(local(logado(email).postForm("/curadoria/" + id + "/aprovar", CHECKLIST_COMPLETO)))
                .endsWith("/curadoria");
    }

    private long enviado(Navegador autor, String titulo, String contexto) throws IOException, InterruptedException {
        var resposta = autor.postForm("/cenarios", completo(titulo, contexto));
        var id = ID_NO_EDITOR.matcher(local(resposta));
        assertThat(id.find()).as("rascunho salvo").isTrue();
        long cenario = Long.parseLong(id.group(1));
        // Como o navegador, segue o redirecionamento: o aviso "Rascunho salvo." não fica para a próxima vez.
        autor.get(local(resposta).replaceFirst("^https?://[^/]+", ""));
        assertThat(local(autor.postForm("/cenarios/" + cenario, completo(titulo, contexto) + "&acao=enviar")))
                .endsWith("/cenarios/enviado");
        return cenario;
    }

    /** Muda só o contexto da versão em rascunho e envia de novo à curadoria. */
    private void reenviar(Navegador autor, long id, String titulo, String contexto)
            throws IOException, InterruptedException {
        assertThat(local(autor.postForm("/cenarios/" + id, completo(titulo, contexto) + "&acao=enviar")))
                .endsWith("/cenarios/enviado");
    }

    private static String completo(String titulo, String contexto) {
        return "titulo=" + cod(titulo)
                + "&personagem=" + cod("Navegador português")
                + "&papelDaIa=" + cod("Conta a viagem em primeira pessoa")
                + "&contexto=" + cod(contexto)
                + "&objetivos=" + cod("Compreender as grandes navegações")
                + "&componente=" + cod("História")
                + "&ano=7"
                + "&habilidades=" + HABILIDADE
                + "&limitesDeAssunto=" + cod("Não inventar fatos")
                + "&fontes=" + cod("Livro didático da rede")
                + "&faixaEtaria=" + cod("12 a 13 anos")
                + "&duracaoPrevistaMin=15"
                + "&perguntasDisparadoras=" + cod("Por que os portugueses navegaram?");
    }

    private String novoUsuario(Perfil... perfis) {
        String email = "versoes" + SEQUENCIA.incrementAndGet() + "@escola.test";
        cadastro.cadastrar("Pessoa de Teste", email, SENHA, Set.of(perfis));
        return email;
    }

    private Navegador logado(String email) throws IOException, InterruptedException {
        return Navegador.logado(porta, email, SENHA);
    }
}
