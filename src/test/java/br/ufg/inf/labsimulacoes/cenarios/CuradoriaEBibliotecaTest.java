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

/**
 * Critérios de aceite da issue 23: fila de curadoria com aprovar e devolver (RF-08, RN-05, RN-07,
 * RN-36) e biblioteca de cenários aprovados em lista (RF-12, RN-01, RN-37). Requisições HTTP reais.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InfraestruturaDeTeste.class)
class CuradoriaEBibliotecaTest {

    private static final String SENHA = "uma senha de teste";
    private static final Pattern ID_NO_EDITOR = Pattern.compile("/cenarios/(\\d+)/editar");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String HABILIDADE = "EF07HI97";

    @LocalServerPort
    private int porta;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private Habilidades habilidades;

    @BeforeEach
    void prepararCatalogo() {
        habilidades.gravar(List.of(new HabilidadeBncc(
                HABILIDADE, "Explicar o significado das grandes navegações.", "EF", List.of(7), "História")));
    }

    // ---------------------------------------------------------------------------------------------
    // Acesso (RN-05, RN-37)
    // ---------------------------------------------------------------------------------------------

    @Test
    void soOCuradorAbreAFila() throws Exception {
        assertThat(logado(novoUsuario(Perfil.PROFESSOR)).get("/curadoria").statusCode()).isEqualTo(403);
        assertThat(logado(novoUsuario(Perfil.ADMINISTRADOR)).get("/curadoria").statusCode()).isEqualTo(403);
        assertThat(logado(novoUsuario(Perfil.CURADOR)).get("/curadoria").statusCode()).isEqualTo(200);
    }

    @Test
    void administradorNaoAbreABiblioteca() throws Exception {
        assertThat(logado(novoUsuario(Perfil.ADMINISTRADOR)).get("/biblioteca").statusCode()).isEqualTo(403);
    }

    @Test
    void professorNaoAprovaNemDevolvePorRequisicaoDireta() throws Exception {
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Direto"));
        var outro = logado(novoUsuario(Perfil.PROFESSOR));

        assertThat(outro.postForm("/curadoria/" + id + "/aprovar", "").statusCode()).isEqualTo(403);
        assertThat(outro.postForm("/curadoria/" + id + "/devolver", "comentario=x").statusCode()).isEqualTo(403);
    }

    @Test
    void inicioDoCuradorLevaAFilaEABiblioteca() throws Exception {
        var corpo = logado(novoUsuario(Perfil.CURADOR)).get("/").body();

        assertThat(corpo).contains("href=\"/curadoria\"").contains("href=\"/biblioteca\"");
    }

    // ---------------------------------------------------------------------------------------------
    // Fila e aprovação (RF-08, RN-36)
    // ---------------------------------------------------------------------------------------------

    @Test
    void filaMostraOCenarioEnviadoComOConteudoEOBotaoDeAprovar() throws Exception {
        String titulo = titulo("Na fila");
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo);

        var corpo = logado(novoUsuario(Perfil.CURADOR)).get("/curadoria?cenario=" + id).body();

        assertThat(corpo)
                .contains(titulo)
                .contains("Explicar o significado das grandes navegações.")
                .contains("Não inventar fatos")
                .contains("/curadoria/" + id + "/aprovar")
                .doesNotContain("Conflito de autoria");
    }

    /** Critério de aceite: o botão de aprovar não aparece para o autor, mesmo com o perfil Curador. */
    @Test
    void autorComPerfilCuradorNaoVeOBotaoDeAprovar() throws Exception {
        var autora = logado(novoUsuario(Perfil.PROFESSOR, Perfil.CURADOR));
        long id = enviado(autora, titulo("Da própria curadora"));

        var corpo = autora.get("/curadoria?cenario=" + id).body();

        assertThat(corpo).contains("Conflito de autoria").doesNotContain("/curadoria/" + id + "/aprovar");
        // E o domínio também recusa, se alguém mandar a requisição mesmo assim.
        autora.postForm("/curadoria/" + id + "/aprovar", "");
        assertThat(autora.get("/curadoria?cenario=" + id).body()).contains("Ninguém aprova um cenário que criou");
    }

    @Test
    void aprovacaoTiraDaFilaEPoeNaBiblioteca() throws Exception {
        String titulo = titulo("Aprovado");
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo);
        var curador = logado(novoUsuario(Perfil.CURADOR));

        var resposta = curador.postForm("/curadoria/" + id + "/aprovar", "");

        assertThat(local(resposta)).endsWith("/curadoria");
        assertThat(curador.get("/curadoria").body()).contains("foi aprovado e já está na biblioteca")
                .doesNotContain("cenario=" + id + "\"");
        var professor = logado(novoUsuario(Perfil.PROFESSOR));
        assertThat(professor.get("/biblioteca?pagina=0").body())
                .contains("/biblioteca/" + id)
                .contains("/sessoes/nova?cenario=" + id);
        assertThat(professor.get("/biblioteca/" + id).body())
                .contains(titulo)
                .contains("Aprovado")
                .contains("Explicar o significado das grandes navegações.")
                .contains("Por que os portugueses navegaram?");
    }

    // ---------------------------------------------------------------------------------------------
    // Devolução (RN-07)
    // ---------------------------------------------------------------------------------------------

    /** Critério de aceite: devolver exige comentário, e o diálogo volta aberto com o aviso. */
    @Test
    void devolucaoSemComentarioERecusada() throws Exception {
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Sem comentário"));
        var curador = logado(novoUsuario(Perfil.CURADOR));

        var resposta = curador.postForm("/curadoria/" + id + "/devolver", "comentario=+++");

        assertThat(local(resposta)).endsWith("/curadoria?cenario=" + id);
        assertThat(curador.get("/curadoria?cenario=" + id).body())
                .contains("Para devolver, escreva um comentário")
                .contains("data-abrir=\"true\"")
                .contains("/curadoria/" + id + "/aprovar");
    }

    @Test
    void devolucaoVoltaAoAutorComOComentario() throws Exception {
        String titulo = titulo("Devolvido");
        var autora = logado(novoUsuario(Perfil.PROFESSOR));
        long id = enviado(autora, titulo);
        var curador = logado(novoUsuario(Perfil.CURADOR));

        curador.postForm("/curadoria/" + id + "/devolver", "comentario=" + cod("Revise as fontes."));

        assertThat(curador.get("/curadoria").body()).contains("voltou ao autor como rascunho");
        assertThat(autora.get("/cenarios").body())
                .contains(titulo)
                .contains("Devolvido")
                .contains("Revise as fontes.")
                .contains("/cenarios/" + id + "/editar");
        assertThat(autora.get("/cenarios/" + id + "/editar").body())
                .contains("Devolvido pela curadoria")
                .contains("Revise as fontes.");
    }

    // ---------------------------------------------------------------------------------------------
    // Biblioteca (RF-12, RN-01)
    // ---------------------------------------------------------------------------------------------

    @Test
    void cenarioEmCuradoriaNaoApareceNaBibliotecaNemNoDetalhe() throws Exception {
        long id = enviado(logado(novoUsuario(Perfil.PROFESSOR)), titulo("Ainda em curadoria"));
        var professor = logado(novoUsuario(Perfil.PROFESSOR));

        var detalhe = professor.get("/biblioteca/" + id);

        assertThat(detalhe.statusCode()).isEqualTo(302);
        assertThat(local(detalhe)).endsWith("/biblioteca");
        assertThat(professor.get("/biblioteca").body())
                .contains("Este cenário não está disponível na biblioteca.")
                .doesNotContain("/biblioteca/" + id + "\"");
    }

    // ---------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------

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
                + "&ano=7"
                + "&habilidades=" + HABILIDADE
                + "&limitesDeAssunto=" + cod("Não inventar fatos")
                + "&fontes=" + cod("Livro didático da rede")
                + "&faixaEtaria=" + cod("12 a 13 anos")
                + "&duracaoPrevistaMin=15"
                + "&perguntasDisparadoras=" + cod("Por que os portugueses navegaram?");
    }

    private String novoUsuario(Perfil... perfis) {
        String email = "curadoria" + SEQUENCIA.incrementAndGet() + "@escola.test";
        cadastro.cadastrar("Pessoa de Teste", email, SENHA, Set.of(perfis));
        return email;
    }

    private Navegador logado(String email) throws IOException, InterruptedException {
        return Navegador.logado(porta, email, SENHA);
    }
}
