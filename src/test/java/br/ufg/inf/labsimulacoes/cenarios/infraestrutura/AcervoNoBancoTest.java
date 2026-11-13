package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Pagina;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TrechoDoAcervo;
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
 * Acervo do curador no PostgreSQL real (RN-37). Cada teste desfaz o que gravou, e nenhum outro teste
 * deste contexto confirma cenário, então o acervo começa vazio.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class AcervoNoBancoTest {

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
        autora = inserirUsuario("autora-acervo@escola.test");
        outraAutora = inserirUsuario("outra-acervo@escola.test");
        curadora = new Revisor(inserirUsuario("curadora-acervo@escola.test"), true);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
    }

    @Test
    void acervoVazioDevolvePaginaVazia() {
        TrechoDoAcervo trecho = cenarios.listarTodos(null, new Pagina(0, 20));

        assertThat(trecho.itens()).isEmpty();
        assertThat(trecho.temProxima()).isFalse();
    }

    /** RN-37: todos os status e todos os autores, inclusive o que a biblioteca deixa de fora. */
    @Test
    void trazTodosOsStatusDeTodosOsAutores() {
        long rascunho = rascunho("A", autora);
        long emCuradoria = emCuradoria("B", outraAutora).id();
        long devolvido = devolvido("C");
        long aprovado = aprovado("D", outraAutora);
        long suspenso = aprovado("E", autora);
        long arquivado = aprovado("F", autora);
        jdbc.update("UPDATE cenario SET status = 'SUSPENSO' WHERE id = ?", suspenso);
        jdbc.update("UPDATE cenario SET status = 'ARQUIVADO' WHERE id = ?", arquivado);

        assertThat(ids(cenarios.listarTodos(null, new Pagina(0, 20))))
                .containsExactly(rascunho, emCuradoria, devolvido, aprovado, suspenso, arquivado);
    }

    @Test
    void filtroPorStatusTrazSoOsDaqueleStatus() {
        rascunho("A", autora);
        long primeiro = emCuradoria("B", autora).id();
        aprovado("C", autora);
        long segundo = emCuradoria("D", outraAutora).id();

        assertThat(ids(cenarios.listarTodos(StatusDoCenario.EM_CURADORIA, new Pagina(0, 20))))
                .containsExactly(primeiro, segundo);
        assertThat(cenarios.listarTodos(StatusDoCenario.ARQUIVADO, new Pagina(0, 20)).itens()).isEmpty();
    }

    /** Cada item é o mesmo cenário que a busca por id devolve: versão mais recente e última devolução. */
    @Test
    void itemTrazAVersaoMaisRecenteEAUltimaDevolucao() {
        long devolvido = devolvido("Devolvido");

        Cenario item = cenarios.listarTodos(null, new Pagina(0, 20)).itens().get(0);

        assertThat(item).isEqualTo(cenarios.buscar(devolvido).orElseThrow());
        assertThat(item.status()).isEqualTo(StatusDoCenario.RASCUNHO);
        assertThat(item.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(item.ultimaDevolucao().comentario()).isEqualTo("Corrigir");
        assertThat(item.autorId()).isEqualTo(autora);
    }

    /** Só a versão mais recente entra: um cenário com várias versões aparece uma vez. */
    @Test
    void cenarioComVariasVersoesApareceUmaVezComAMaisRecente() {
        long id = aprovado("Primeira", autora);
        jdbc.update("""
                INSERT INTO cenario_versao (cenario_id, numero, status, titulo, conteudo, criado_por_id)
                SELECT cenario_id, 2, 'RASCUNHO', 'Segunda', conteudo, criado_por_id
                FROM cenario_versao WHERE cenario_id = ? AND numero = 1""", id);

        List<Cenario> itens = cenarios.listarTodos(null, new Pagina(0, 20)).itens();

        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).versao().numero()).isEqualTo(2);
        assertThat(itens.get(0).versao().conteudo().titulo()).isEqualTo("Segunda");
    }

    @Test
    void ordemPorTituloComDesempatePorId() {
        long beta = rascunho("Beta", autora);
        long alfaPrimeiro = emCuradoria("Alfa", autora).id();
        long alfaSegundo = aprovado("Alfa", outraAutora);

        assertThat(ids(cenarios.listarTodos(null, new Pagina(0, 20))))
                .containsExactly(alfaPrimeiro, alfaSegundo, beta);
    }

    @Test
    void paginasSeguidasCobremOAcervoSemRepetir() {
        List<Long> todos = List.of(
                rascunho("A", autora), emCuradoria("B", autora).id(), aprovado("C", autora),
                rascunho("D", outraAutora), devolvido("E"));

        TrechoDoAcervo primeira = cenarios.listarTodos(null, new Pagina(0, 2));
        TrechoDoAcervo segunda = cenarios.listarTodos(null, new Pagina(1, 2));
        TrechoDoAcervo terceira = cenarios.listarTodos(null, new Pagina(2, 2));

        assertThat(primeira.temProxima()).isTrue();
        assertThat(segunda.temProxima()).isTrue();
        assertThat(terceira.temProxima()).isFalse();
        assertThat(List.of(ids(primeira), ids(segunda), ids(terceira)).stream().flatMap(List::stream).toList())
                .isEqualTo(todos);
    }

    @Test
    void paginaCheiaSemProximaNaoMarcaProxima() {
        rascunho("A", autora);
        rascunho("B", autora);

        TrechoDoAcervo trecho = cenarios.listarTodos(null, new Pagina(0, 2));

        assertThat(trecho.itens()).hasSize(2);
        assertThat(trecho.temProxima()).isFalse();
    }

    // ---------------------------------------------------------------------------------------------
    // Cenários de um autor (RN-37, RN-07)
    // ---------------------------------------------------------------------------------------------

    /** Só os do autor, em qualquer status, os mais novos primeiro. */
    @Test
    void doAutorTrazSoOsDeleEmQualquerStatusDoMaisNovoAoMaisAntigo() {
        long rascunho = rascunho("Z", autora);
        rascunho("De outra", outraAutora);
        long emCuradoria = emCuradoria("A", autora).id();
        long aprovado = aprovado("M", autora);

        assertThat(ids(cenarios.listarDoAutor(autora, new Pagina(0, 20))))
                .containsExactly(aprovado, emCuradoria, rascunho);
        assertThat(cenarios.listarDoAutor(curadora.id(), new Pagina(0, 20)).itens()).isEmpty();
    }

    /** O autor precisa ler o comentário da devolução para corrigir o rascunho (RN-07). */
    @Test
    void doAutorTrazAUltimaDevolucao() {
        long devolvido = devolvido("Devolvido");

        Cenario item = cenarios.listarDoAutor(autora, new Pagina(0, 20)).itens().get(0);

        assertThat(item).isEqualTo(cenarios.buscar(devolvido).orElseThrow());
        assertThat(item.ultimaDevolucao().comentario()).isEqualTo("Corrigir");
    }

    @Test
    void doAutorPaginaSemRepetir() {
        long primeiro = rascunho("A", autora);
        long segundo = rascunho("B", autora);
        long terceiro = rascunho("C", autora);

        TrechoDoAcervo inicio = cenarios.listarDoAutor(autora, new Pagina(0, 2));
        TrechoDoAcervo fim = cenarios.listarDoAutor(autora, new Pagina(1, 2));

        assertThat(inicio.temProxima()).isTrue();
        assertThat(ids(inicio)).containsExactly(terceiro, segundo);
        assertThat(fim.temProxima()).isFalse();
        assertThat(ids(fim)).containsExactly(primeiro);
    }

    private static List<Long> ids(TrechoDoAcervo trecho) {
        return trecho.itens().stream().map(Cenario::id).toList();
    }

    private long rascunho(String titulo, long autor) {
        return cenarios.salvar(Cenario.novo(autor, conteudo(titulo))).id();
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
