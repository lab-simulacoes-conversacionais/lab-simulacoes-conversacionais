package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Pagina;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Persistência dos cenários no PostgreSQL real. Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class CenariosJdbcTest {

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private long autor;

    @BeforeEach
    void prepararAutorECatalogo() {
        autor = jdbc.queryForObject("""
                INSERT INTO usuario (nome, email, senha_hash)
                VALUES ('Autora', 'autora-jdbc@escola.test', 'hash') RETURNING id""", Long.class);
        habilidades.gravar(List.of(
                habilidade("EF08HI03"),
                habilidade("EF08HI01")));
    }

    private static HabilidadeBncc habilidade(String codigo) {
        return new HabilidadeBncc(codigo, "Descrição", "EF", List.of(8), "História");
    }

    @Test
    void salvarGeraIdentificadoresEDevolveOMesmoAoBuscar() {
        Cenario salvo = cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo()));

        assertThat(salvo.id()).isNotNull();
        assertThat(salvo.versao().id()).isNotNull();
        assertThat(cenarios.buscar(salvo.id())).contains(salvo);
    }

    @Test
    void rascunhoIncompletoEGravadoComAsColunasVazias() {
        Cenario salvo = cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.soComTitulo()));

        assertThat(cenarios.buscar(salvo.id())).contains(salvo);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM versao_habilidade WHERE cenario_versao_id = ?",
                Integer.class,
                salvo.versao().id())).isZero();
    }

    /** Stack, seção 6: texto em JSONB; o que serve para filtros e integridade, em colunas. */
    @Test
    void conteudoTextualFicaNoJsonbEOsFiltrosEmColunas() {
        Cenario salvo = cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo()));

        var linha = jdbc.queryForMap("""
                SELECT titulo, componente, etapa, ano, faixa_etaria, duracao_prevista_min,
                       conteudo ->> 'personagem' AS personagem,
                       conteudo -> 'objetivos' ->> 0 AS objetivo
                FROM cenario_versao WHERE id = ?""",
                salvo.versao().id());

        assertThat(linha)
                .containsEntry("titulo", "O voo do 14-bis")
                .containsEntry("componente", "História")
                .containsEntry("etapa", "EF")
                .containsEntry("ano", "8")
                .containsEntry("faixa_etaria", "13 a 14 anos")
                .containsEntry("duracao_prevista_min", 20)
                .containsEntry("personagem", "Santos Dumont")
                .containsEntry("objetivo", "Explicar por que o voo por meios próprios foi importante");
    }

    @Test
    void habilidadesFicamEmVersaoHabilidade() {
        var c = ConteudosDeTeste.CURRICULO;
        var conteudo = comHabilidades(c, List.of("EF08HI03", "EF08HI01"));

        Cenario salvo = cenarios.salvar(Cenario.novo(autor, conteudo));

        assertThat(jdbc.queryForList(
                "SELECT habilidade_codigo FROM versao_habilidade WHERE cenario_versao_id = ? ORDER BY 1",
                String.class,
                salvo.versao().id())).containsExactly("EF08HI01", "EF08HI03");
    }

    @Test
    void salvarDeNovoTrocaOConteudoEAsHabilidadesDaMesmaVersao() {
        Cenario salvo = cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo()));
        var novo = comHabilidades(ConteudosDeTeste.CURRICULO, List.of("EF08HI01"));

        Cenario alterado = cenarios.salvar(salvo.alterarRascunho(novo));

        assertThat(alterado.versao().id()).isEqualTo(salvo.versao().id());
        assertThat(cenarios.buscar(salvo.id())).contains(alterado);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM cenario_versao WHERE cenario_id = ?", Integer.class, salvo.id())).isEqualTo(1);
    }

    @Test
    void envioACuradoriaGravaOStatusDoCenarioEDaVersao() {
        Cenario salvo = cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo()));

        cenarios.salvar(salvo.enviarACuradoria(autor));

        var lido = cenarios.buscar(salvo.id()).orElseThrow();
        assertThat(lido.status()).isEqualTo(StatusDoCenario.EM_CURADORIA);
        assertThat(lido.versao().status()).isEqualTo(StatusDaVersao.EM_CURADORIA);
    }

    @Test
    void buscarTrazAVersaoMaisRecente() {
        Cenario salvo = cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo()));
        jdbc.update("""
                INSERT INTO cenario_versao (cenario_id, numero, titulo, conteudo, criado_por_id)
                VALUES (?, 2, 'Segunda', '{}', ?)""", salvo.id(), autor);

        var lido = cenarios.buscarParaAlterar(salvo.id()).orElseThrow();

        assertThat(lido.versao().numero()).isEqualTo(2);
        assertThat(lido.versao().conteudo().titulo()).isEqualTo("Segunda");
        assertThat(lido.versao().conteudo().roteiro().objetivos()).isEmpty();
    }

    @Test
    void cenarioInexistenteVemVazio() {
        assertThat(cenarios.buscar(-1)).isEmpty();
        assertThat(cenarios.buscarParaAlterar(-1)).isEmpty();
    }

    @Test
    void habilidadeForaDoCatalogoERecusadaPeloBanco() {
        var conteudo = comHabilidades(ConteudosDeTeste.CURRICULO, List.of("EF01XX99"));

        assertThatThrownBy(() -> cenarios.salvar(Cenario.novo(autor, conteudo)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** RN-04: a marca "Sugerido por IA" aponta para os metadados da geração (RNF-10). */
    @Test
    void cenarioSugeridoPelaIaEGravadoComAMarca() {
        long geracao = inserirMetadadosDaGeracao();

        Cenario salvo = cenarios.salvar(Cenario.sugeridoPelaIa(autor, ConteudosDeTeste.completo(), geracao));

        assertThat(salvo.sugeridoPorIa()).isTrue();
        assertThat(salvo.sugestaoDaIa()).isEqualTo(geracao);
        assertThat(cenarios.buscar(salvo.id())).contains(salvo);
        assertThat(cenarios.listarDoAutor(autor, new Pagina(0, 10)).itens()).containsExactly(salvo);
        assertThat(cenarios.listarTodos(StatusDoCenario.RASCUNHO, new Pagina(0, 10)).itens()).contains(salvo);
    }

    @Test
    void marcaDaSugestaoContinuaDepoisDeEnviarACuradoria() {
        long geracao = inserirMetadadosDaGeracao();
        Cenario salvo = cenarios.salvar(Cenario.sugeridoPelaIa(autor, ConteudosDeTeste.completo(), geracao));

        Cenario enviado = cenarios.salvar(salvo.enviarACuradoria(autor));

        assertThat(enviado.sugestaoDaIa()).isEqualTo(geracao);
        assertThat(cenarios.buscar(salvo.id()).orElseThrow().sugestaoDaIa()).isEqualTo(geracao);
    }

    /** RN-04: a retenção apaga os metadados da geração, e o cenário continua marcado. */
    @Test
    void apagarOsMetadadosDaGeracaoMantemAMarca() {
        long geracao = inserirMetadadosDaGeracao();
        Cenario salvo = cenarios.salvar(Cenario.sugeridoPelaIa(autor, ConteudosDeTeste.completo(), geracao));

        jdbc.update("DELETE FROM resposta_ia_meta WHERE id = ?", geracao);

        Cenario lido = cenarios.buscar(salvo.id()).orElseThrow();
        assertThat(lido.sugeridoPorIa()).isTrue();
        assertThat(lido.sugestaoDaIa()).isNull();
    }

    @Test
    void metadadosDaGeracaoSemAMarcaSaoRecusadosPeloBanco() {
        long geracao = inserirMetadadosDaGeracao();
        Cenario salvo = cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo()));

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE cenario SET sugestao_ia_meta_id = ? WHERE id = ?", geracao, salvo.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cenarioCriadoDoZeroFicaSemAMarca() {
        Cenario salvo = cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo()));

        assertThat(cenarios.buscar(salvo.id()).orElseThrow().sugeridoPorIa()).isFalse();
    }

    @Test
    void marcaSemMetadadosDaGeracaoERecusadaPeloBanco() {
        assertThatThrownBy(() -> cenarios.salvar(Cenario.sugeridoPelaIa(autor, ConteudosDeTeste.completo(), -1L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long inserirMetadadosDaGeracao() {
        return jdbc.queryForObject("""
                INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt)
                VALUES ('openai', 'modelo', 'sugestao-v1') RETURNING id""", Long.class);
    }

    private static ConteudoDoCenario comHabilidades(VinculoCurricular c, List<String> codigos) {
        return new ConteudoDoCenario(
                "O voo do 14-bis",
                ConteudosDeTeste.ROTEIRO,
                new VinculoCurricular(c.componente(), c.etapa(), c.ano(), codigos),
                "13 a 14 anos",
                20);
    }
}
