package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.BuscaNaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.OpcoesDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Pagina;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RoteiroDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TrechoDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca e filtros da biblioteca no PostgreSQL real (RF-12, RN-01). Três cenários aprovados de
 * componentes, anos e habilidades diferentes, e um rascunho que casaria com quase tudo, para provar
 * que só os aprovados aparecem. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class FiltrosDaBibliotecaNoBancoTest {

    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Pagina PAGINA = new Pagina(0, 20);

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private long autora;
    private Revisor curadora;
    private long voo;
    private long celula;
    private long revolucao;

    @BeforeEach
    void prepararBiblioteca() {
        autora = inserirUsuario("autora-filtros@escola.test");
        curadora = new Revisor(inserirUsuario("curadora-filtros@escola.test"), true);
        habilidades.gravar(List.of(
                new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História"),
                new HabilidadeBncc("EF07HI01", "Descrição", "EF", List.of(7), "História"),
                new HabilidadeBncc("EF06CI01", "Descrição", "EF", List.of(6), "Ciências"),
                new HabilidadeBncc("EF06GE01", "Descrição", "EF", List.of(6), "Geografia")));
        voo = aprovado(conteudo("O voo do 14-bis", "Santos Dumont", "Paris, 1906",
                "Explicar o voo por meios próprios", "História", 8, "EF08HI03"));
        celula = aprovado(conteudo("A célula por dentro", "Uma célula animal", "Visão ao microscópio",
                "Explicar a função da membrana", "Ciências", 6, "EF06CI01"));
        revolucao = aprovado(conteudo("Revolução Francesa", "Jornalista de 1789", "Paris, 14 de julho",
                "Relacionar a queda da Bastilha ao fim do Antigo Regime", "História", 7, "EF07HI01"));
        cenarios.salvar(Cenario.novo(autora, conteudo("Outro voo de 14-bis", "Santos Dumont", "Paris",
                "Explicar o voo", "Geografia", 6, "EF06GE01")));
    }

    @Test
    void semFiltroTrazTodosOsAprovadosEmOrdemDeTitulo() {
        assertThat(ids(BuscaNaBiblioteca.TUDO)).containsExactly(celula, voo, revolucao);
    }

    @Test
    void textoAchaNoTitulo() {
        assertThat(ids(texto("voo"))).containsExactly(voo);
    }

    /** O tema também aparece no personagem, no contexto, nos objetivos e no componente. */
    @Test
    void textoAchaNoPersonagemNoContextoNosObjetivosENoComponente() {
        assertThat(ids(texto("dumont"))).containsExactly(voo);
        assertThat(ids(texto("microscópio"))).containsExactly(celula);
        assertThat(ids(texto("bastilha"))).containsExactly(revolucao);
        assertThat(ids(texto("ciencias"))).containsExactly(celula);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CÉLULA", "celula", "Celula", "célula"})
    void textoNaoDiferenciaMaiusculasNemAcentos(String digitado) {
        assertThat(ids(texto(digitado))).containsExactly(celula);
    }

    @Test
    void textoEmVariosCenariosTrazTodos() {
        assertThat(ids(texto("paris"))).containsExactly(voo, revolucao);
    }

    /** % e _ são procurados ao pé da letra, e não viram curingas do LIKE. */
    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\"})
    void curingaDoLikeEProcuradoAoPeDaLetra(String digitado) {
        assertThat(ids(texto(digitado))).isEmpty();
    }

    /** Os objetivos entram como texto, e não como o JSON cru, que tem aspas e colchetes em todo cenário. */
    @ParameterizedTest
    @ValueSource(strings = {"\"", "[", "]", "\",\""})
    void sinaisDoJsonNaoCasamComTodosOsCenarios(String digitado) {
        assertThat(ids(texto(digitado))).isEmpty();
    }

    /** No JSON, as aspas de um objetivo viram \"; a busca olha o texto, sem esse escape. */
    @Test
    void objetivoComAspasEProcuradoComoTexto() {
        long comAspas = aprovado(conteudo("O 14-bis em Bagatelle", "Santos Dumont", "Paris",
                "Explicar o \"voo\" por meios próprios", "História", 8, "EF08HI03"));

        assertThat(ids(texto("\\"))).isEmpty();
        assertThat(ids(texto("o \"voo\""))).containsExactly(comAspas);
    }

    /** A regra de acento é a mesma no Java e no banco, inclusive para o acento separado da letra. */
    @Test
    void acentoSeguidoAMesmaRegraDosDoisLados() {
        long cafe = aprovado(conteudo("Café colonial", "Um tropeiro", "Vale do Paraíba",
                "Explicar o ciclo do café", "História", 8, "EF08HI03"));
        long angstrom = aprovado(conteudo("Ångström e a luz", "Anders Ångström", "Uppsala, 1868",
                "Explicar o espectro do Sol", "Ciências", 6, "EF06CI01"));

        assertThat(ids(texto("cafe colonial"))).containsExactly(cafe);
        assertThat(ids(texto("café colonial"))).containsExactly(cafe);
        assertThat(ids(texto("angstrom"))).containsExactly(angstrom);
    }

    @Test
    void componenteSemDiferenciarMaiusculasNemAcentos() {
        assertThat(ids(new BuscaNaBiblioteca(null, "história", null, null))).containsExactly(voo, revolucao);
        assertThat(ids(new BuscaNaBiblioteca(null, "Historia", null, null))).containsExactly(voo, revolucao);
        assertThat(ids(new BuscaNaBiblioteca(null, "Matemática", null, null))).isEmpty();
    }

    @Test
    void ano() {
        assertThat(ids(new BuscaNaBiblioteca(null, null, 7, null))).containsExactly(revolucao);
        assertThat(ids(new BuscaNaBiblioteca(null, null, 9, null))).isEmpty();
    }

    @Test
    void habilidade() {
        assertThat(ids(new BuscaNaBiblioteca(null, null, null, "ef08hi03"))).containsExactly(voo);
        assertThat(ids(new BuscaNaBiblioteca(null, null, null, "EF06GE01"))).isEmpty();
    }

    @Test
    void filtrosSeCombinam() {
        assertThat(ids(new BuscaNaBiblioteca(null, "História", 8, null))).containsExactly(voo);
        assertThat(ids(new BuscaNaBiblioteca("paris", "História", 7, "EF07HI01"))).containsExactly(revolucao);
        assertThat(ids(new BuscaNaBiblioteca("célula", "História", null, null))).isEmpty();
    }

    /** RN-01: o rascunho casa com "voo", Geografia, 6º ano e EF06GE01, mas não está na biblioteca. */
    @Test
    void naoAprovadoNaoApareceMesmoCasandoComOsFiltros() {
        assertThat(ids(new BuscaNaBiblioteca("voo", null, null, null))).containsExactly(voo);
        assertThat(ids(new BuscaNaBiblioteca(null, "Geografia", null, null))).isEmpty();
    }

    @Test
    void paginaComFiltroSabeSeHaProxima() {
        TrechoDaBiblioteca primeira = cenarios.listarAprovados(new BuscaNaBiblioteca(null, "História", null, null),
                new Pagina(0, 1));
        TrechoDaBiblioteca segunda = cenarios.listarAprovados(new BuscaNaBiblioteca(null, "História", null, null),
                new Pagina(1, 1));

        assertThat(primeira.itens()).extracting(CenarioNaBiblioteca::cenarioId).containsExactly(voo);
        assertThat(primeira.temProxima()).isTrue();
        assertThat(segunda.itens()).extracting(CenarioNaBiblioteca::cenarioId).containsExactly(revolucao);
        assertThat(segunda.temProxima()).isFalse();
    }

    /** As opções vêm só dos aprovados: a Geografia do rascunho fica de fora. */
    @Test
    void opcoesDosFiltrosVemDosAprovados() {
        OpcoesDaBiblioteca opcoes = cenarios.opcoesDaBiblioteca();

        assertThat(opcoes.componentes()).containsExactly("Ciências", "História");
        assertThat(opcoes.anos()).containsExactly(6, 7, 8);
        assertThat(opcoes.habilidades()).containsExactly("EF06CI01", "EF07HI01", "EF08HI03");
    }

    private List<Long> ids(BuscaNaBiblioteca busca) {
        return cenarios.listarAprovados(busca, PAGINA).itens().stream().map(CenarioNaBiblioteca::cenarioId).toList();
    }

    private static BuscaNaBiblioteca texto(String texto) {
        return new BuscaNaBiblioteca(texto, null, null, null);
    }

    private long aprovado(ConteudoDoCenario conteudo) {
        Cenario salvo = cenarios.salvar(Cenario.novo(autora, conteudo));
        Cenario enviado = cenarios.salvar(salvo.enviarACuradoria(autora));
        return cenarios.salvar(enviado.aprovar(curadora, AGORA)).id();
    }

    private static ConteudoDoCenario conteudo(
            String titulo, String personagem, String contexto, String objetivo, String componente, int ano,
            String habilidade) {
        var roteiro = new RoteiroDoCenario(personagem, "Responder em primeira pessoa", contexto, List.of(objetivo),
                List.of("Política partidária"), List.of("Livro didático"), List.of());
        var curriculo = new VinculoCurricular(componente, "EF", ano, List.of(habilidade));
        return new ConteudoDoCenario(titulo, roteiro, curriculo, "11 a 14 anos", 20);
    }

    private long inserirUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
    }
}
