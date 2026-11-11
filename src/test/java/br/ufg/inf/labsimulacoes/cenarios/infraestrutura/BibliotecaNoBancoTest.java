package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Pagina;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TrechoDaBiblioteca;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Biblioteca de cenários no PostgreSQL real (RF-12, RN-01). Cada teste desfaz o que gravou, e nenhum
 * outro teste deste contexto confirma cenário aprovado, então a biblioteca começa vazia.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class BibliotecaNoBancoTest {

    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private long autora;
    private long outraAutora;
    private Revisor curadora;

    @BeforeEach
    void prepararUsuariosECatalogo() {
        autora = inserirUsuario("autora-biblioteca@escola.test");
        outraAutora = inserirUsuario("outra-biblioteca@escola.test");
        curadora = new Revisor(inserirUsuario("curadora-biblioteca@escola.test"), true);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
    }

    @Test
    void bibliotecaVaziaDevolvePaginaVazia() {
        rascunho("Ainda em rascunho");

        TrechoDaBiblioteca trecho = cenarios.listarAprovados(new Pagina(0, 20));

        assertThat(trecho.itens()).isEmpty();
        assertThat(trecho.temProxima()).isFalse();
    }

    /** RN-01, RN-09: só o cenário Aprovado com versão vigente entra na biblioteca. */
    @Test
    void soAprovadoComVersaoVigenteAparece() {
        rascunho("Rascunho");
        emCuradoria("Em curadoria", autora);
        devolvido("Devolvido");
        long suspenso = aprovado("Suspenso", autora);
        long arquivado = aprovado("Arquivado", autora);
        long aprovado = aprovado("Aprovado", autora);
        jdbc.update("UPDATE cenario SET status = 'SUSPENSO' WHERE id = ?", suspenso);
        jdbc.update("UPDATE cenario SET status = 'ARQUIVADO' WHERE id = ?", arquivado);

        assertThat(ids(cenarios.listarAprovados(new Pagina(0, 20)))).containsExactly(aprovado);
    }

    /** Os aprovados de todos os autores entram, com a versão vigente e o que a lista mostra. */
    @Test
    void trazOsAprovadosDeTodosOsAutoresComAVersaoVigente() {
        long daAutora = aprovado("Da autora", autora);
        long daOutra = aprovado("Da outra autora", outraAutora);

        List<CenarioNaBiblioteca> itens = cenarios.listarAprovados(new Pagina(0, 20)).itens();

        assertThat(itens).extracting(CenarioNaBiblioteca::cenarioId).containsExactly(daAutora, daOutra);
        var versao = itens.get(0).versao();
        assertThat(versao).isEqualTo(cenarios.versaoVigente(daAutora).orElseThrow());
        assertThat(versao.conteudo().titulo()).isEqualTo("Da autora");
        assertThat(versao.conteudo().curriculo().habilidades()).containsExactly("EF08HI03");
        assertThat(versao.aprovacao().em()).isEqualTo(AGORA);
    }

    /** Ordem por título; o id desempata os títulos iguais. */
    @Test
    void ordemPorTituloComDesempatePorId() {
        long beta = aprovado("Beta", autora);
        long alfaPrimeiro = aprovado("Alfa", autora);
        long alfaSegundo = aprovado("Alfa", outraAutora);

        assertThat(ids(cenarios.listarAprovados(new Pagina(0, 20)))).containsExactly(alfaPrimeiro, alfaSegundo, beta);
    }

    @Test
    void mesmaConsultaDevolveSempreAMesmaOrdem() {
        for (int i = 0; i < 5; i++) {
            aprovado("Mesmo título", i % 2 == 0 ? autora : outraAutora);
        }

        var primeira = ids(cenarios.listarAprovados(new Pagina(0, 20)));

        assertThat(primeira).isSorted();
        for (int i = 0; i < 3; i++) {
            assertThat(ids(cenarios.listarAprovados(new Pagina(0, 20)))).isEqualTo(primeira);
        }
    }

    /** Páginas seguidas não repetem nem pulam cenário, e a última diz que não há próxima. */
    @Test
    void paginasSeguidasCobremABibliotecaSemRepetir() {
        List<Long> todos = List.of(
                aprovado("A", autora), aprovado("B", autora), aprovado("C", autora),
                aprovado("D", autora), aprovado("E", autora));

        TrechoDaBiblioteca primeira = cenarios.listarAprovados(new Pagina(0, 2));
        TrechoDaBiblioteca segunda = cenarios.listarAprovados(new Pagina(1, 2));
        TrechoDaBiblioteca terceira = cenarios.listarAprovados(new Pagina(2, 2));
        TrechoDaBiblioteca alemDoFim = cenarios.listarAprovados(new Pagina(3, 2));

        assertThat(primeira.temProxima()).isTrue();
        assertThat(segunda.temProxima()).isTrue();
        assertThat(terceira.temProxima()).isFalse();
        assertThat(alemDoFim.itens()).isEmpty();
        assertThat(alemDoFim.temProxima()).isFalse();
        assertThat(List.of(ids(primeira), ids(segunda), ids(terceira)).stream().flatMap(List::stream).toList())
                .isEqualTo(todos);
    }

    /** Página exatamente cheia: o item a mais só é pedido para saber se há próxima. */
    @Test
    void paginaCheiaSemProximaNaoMarcaProxima() {
        aprovado("A", autora);
        aprovado("B", autora);

        TrechoDaBiblioteca trecho = cenarios.listarAprovados(new Pagina(0, 2));

        assertThat(trecho.itens()).hasSize(2);
        assertThat(trecho.temProxima()).isFalse();
    }

    @Test
    void paginaRespeitaOTamanhoMaximo() {
        for (int i = 0; i < Pagina.TAMANHO_MAXIMO + 1; i++) {
            aprovado("Cenário " + i, autora);
        }

        TrechoDaBiblioteca trecho = cenarios.listarAprovados(new Pagina(0, Pagina.TAMANHO_MAXIMO));

        assertThat(trecho.itens()).hasSize(Pagina.TAMANHO_MAXIMO);
        assertThat(trecho.temProxima()).isTrue();
    }

    private static List<Long> ids(TrechoDaBiblioteca trecho) {
        return trecho.itens().stream().map(CenarioNaBiblioteca::cenarioId).toList();
    }

    private long rascunho(String titulo) {
        return cenarios.salvar(Cenario.novo(autora, conteudo(titulo))).id();
    }

    private Cenario emCuradoria(String titulo, long autor) {
        Cenario salvo = cenarios.salvar(Cenario.novo(autor, conteudo(titulo)));
        return cenarios.salvar(salvo.enviarACuradoria(autor));
    }

    private long devolvido(String titulo) {
        return cenarios.salvar(emCuradoria(titulo, autora).devolver(curadora, "Corrigir", AGORA)).id();
    }

    private long aprovado(String titulo, long autor) {
        return cenarios.salvar(emCuradoria(titulo, autor).aprovar(curadora, AGORA)).id();
    }

    private static ConteudoDoCenario conteudo(String titulo) {
        var completo = ConteudosDeTeste.completo();
        return new ConteudoDoCenario(
                titulo, completo.roteiro(), completo.curriculo(), completo.faixaEtaria(), completo.duracaoPrevistaMin());
    }

    private long inserirUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
    }
}
