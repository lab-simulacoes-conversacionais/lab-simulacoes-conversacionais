package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntasDeTeste;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Perguntas de teste do curador no PostgreSQL real (RF-09, RN-06). Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class PerguntasDeTesteJdbcTest {

    @Autowired
    private PerguntasDeTeste perguntas;

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private long autor;
    private long curador;
    private long outroCurador;
    private long versao;
    private long meta;

    @BeforeEach
    void prepararVersaoEmCuradoria() {
        autor = inserirUsuario("autora-teste-jdbc@escola.test");
        curador = inserirUsuario("curadora-teste-jdbc@escola.test");
        outroCurador = inserirUsuario("curador-teste-jdbc@escola.test");
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
        versao = emCuradoria().versao().id();
        meta = inserirMetadados();
    }

    @Test
    void gravarDevolveComIdELerTrazNaOrdemDeGravacao() {
        PerguntaDeTeste primeira = perguntas.gravar(PerguntaDeTeste.exibida(versao, curador, "Oi?", "Olá!", meta));
        PerguntaDeTeste segunda = perguntas.gravar(PerguntaDeTeste.comPerguntaBloqueada(versao, curador, "Impróprio"));
        PerguntaDeTeste terceira = perguntas.gravar(PerguntaDeTeste.comRespostaBloqueada(versao, curador, "E aí?", meta));

        assertThat(primeira.id()).isNotNull();
        assertThat(perguntas.daRodada(versao, curador)).containsExactly(primeira, segunda, terceira);
    }

    /** RN-06: só as perguntas do curador que vai aprovar, sem as bloqueadas. */
    @Test
    void contarIgnoraPerguntaBloqueadaOutroCuradorEOutraVersao() {
        perguntas.gravar(PerguntaDeTeste.exibida(versao, curador, "1", "R", meta));
        perguntas.gravar(PerguntaDeTeste.comRespostaBloqueada(versao, curador, "2", meta));
        perguntas.gravar(PerguntaDeTeste.comPerguntaBloqueada(versao, curador, "3"));
        perguntas.gravar(PerguntaDeTeste.exibida(versao, outroCurador, "4", "R", meta));
        long outraVersao = emCuradoria().versao().id();
        perguntas.gravar(PerguntaDeTeste.exibida(outraVersao, curador, "5", "R", meta));

        assertThat(perguntas.contar(versao, curador)).isEqualTo(2);
        assertThat(perguntas.contar(versao, outroCurador)).isEqualTo(1);
        assertThat(perguntas.contar(outraVersao, curador)).isEqualTo(1);
        assertThat(perguntas.daRodada(versao, outroCurador)).extracting(PerguntaDeTeste::pergunta).containsExactly("4");
    }

    @Test
    void semPerguntasAContagemEZero() {
        assertThat(perguntas.contar(versao, curador)).isZero();
        assertThat(perguntas.daRodada(versao, curador)).isEmpty();
    }

    /** A devolução fecha a rodada de todos os curadores da versão, sem apagar as perguntas. */
    @Test
    void fecharRodadaZeraAContagemEGuardaAsPerguntas() {
        perguntas.gravar(PerguntaDeTeste.exibida(versao, curador, "1", "R", meta));
        perguntas.gravar(PerguntaDeTeste.comPerguntaBloqueada(versao, curador, "2"));
        perguntas.gravar(PerguntaDeTeste.exibida(versao, outroCurador, "3", "R", meta));
        long outraVersao = emCuradoria().versao().id();
        perguntas.gravar(PerguntaDeTeste.exibida(outraVersao, curador, "4", "R", meta));

        int fechadas = perguntas.fecharRodada(versao, inserirDevolucao(versao));

        assertThat(fechadas).isEqualTo(3);
        assertThat(perguntas.contar(versao, curador)).isZero();
        assertThat(perguntas.contar(versao, outroCurador)).isZero();
        assertThat(perguntas.daRodada(versao, curador)).isEmpty();
        assertThat(perguntas.contar(outraVersao, curador)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pergunta_de_teste WHERE cenario_versao_id = ?", Integer.class, versao))
                .isEqualTo(3);
    }

    /** Uma rodada fechada não é reaberta: a segunda devolução só fecha as perguntas novas. */
    @Test
    void segundaDevolucaoFechaSoARodadaNova() {
        perguntas.gravar(PerguntaDeTeste.exibida(versao, curador, "1", "R", meta));
        long primeira = inserirDevolucao(versao);
        perguntas.fecharRodada(versao, primeira);
        perguntas.gravar(PerguntaDeTeste.exibida(versao, curador, "2", "R", meta));

        assertThat(perguntas.contar(versao, curador)).isEqualTo(1);
        assertThat(perguntas.fecharRodada(versao, inserirDevolucao(versao))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pergunta_de_teste WHERE devolucao_id = ?", Integer.class, primeira))
                .isEqualTo(1);
    }

    /** O banco também recusa um desfecho incoerente, além do domínio. Um caso por vez: o erro aborta a transação. */
    @ParameterizedTest(name = "pergunta bloqueada={0}, resposta bloqueada={1}, resposta=[{2}], com metadados={3}")
    @CsvSource({
        "true,  false, Resposta, false",
        "true,  false,         , true",
        "false, true,  Resposta, true",
        "true,  true,          , false",
        "false, false,         , true",
        "false, false, '  ',     true"
    })
    void bancoRecusaDesfechoIncoerente(
            boolean perguntaBloqueada, boolean respostaBloqueada, String resposta, boolean comMetadados) {
        assertThatThrownBy(() -> inserirDireto(perguntaBloqueada, respostaBloqueada, resposta, comMetadados ? meta : null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "pergunta bloqueada={0}, resposta bloqueada={1}, resposta=[{2}], com metadados={3}")
    @CsvSource({
        "true,  false,         , false",
        "false, true,          , true",
        "false, false, Resposta, true",
        "false, false, Resposta, false"
    })
    void bancoAceitaOsDesfechosCoerentes(
            boolean perguntaBloqueada, boolean respostaBloqueada, String resposta, boolean comMetadados) {
        assertThatCode(() -> inserirDireto(perguntaBloqueada, respostaBloqueada, resposta, comMetadados ? meta : null))
                .doesNotThrowAnyException();
    }

    /** Como na mensagem da sala, apagar os metadados não apaga a pergunta de teste. */
    @Test
    void apagarOsMetadadosMantemAPergunta() {
        PerguntaDeTeste gravada = perguntas.gravar(PerguntaDeTeste.exibida(versao, curador, "Oi?", "Olá!", meta));

        jdbc.update("DELETE FROM resposta_ia_meta WHERE id = ?", meta);

        assertThat(perguntas.daRodada(versao, curador))
                .containsExactly(new PerguntaDeTeste(
                        gravada.id(), versao, curador, "Oi?", PerguntaDeTeste.Desfecho.EXIBIDA, "Olá!", null));
    }

    /**
     * RNF-12: a imagem anterior, que não conhece pergunta_de_teste, continua devolvendo e aprovando uma
     * versão que já tem perguntas de teste. Os comandos são os do CenariosJdbc daquela imagem. A devolução
     * feita por ela não fecha a rodada; depois de voltar a esta imagem, aquelas perguntas continuam
     * contando, o que só pode adiantar o checklist, nunca impedir uma aprovação.
     */
    @Test
    void imagemAnteriorDevolveEAprovaUmaVersaoComPerguntasDeTeste() {
        perguntas.gravar(PerguntaDeTeste.exibida(versao, curador, "Oi?", "Olá!", meta));

        assertThatCode(() -> {
            jdbc.update("INSERT INTO devolucao (cenario_versao_id, curador_id, comentario, criado_em) "
                    + "VALUES (?, ?, 'Corrigir', now())", versao, curador);
            jdbc.update("UPDATE cenario_versao SET status = 'RASCUNHO' WHERE id = ?", versao);
            jdbc.update("UPDATE cenario_versao SET status = 'EM_CURADORIA' WHERE id = ?", versao);
            jdbc.update("UPDATE cenario_versao SET status = 'APROVADA', aprovado_por_id = ?, aprovado_em = now() "
                    + "WHERE id = ?", curador, versao);
        }).doesNotThrowAnyException();
        assertThat(perguntas.contar(versao, curador)).isEqualTo(1);
    }

    private Cenario emCuradoria() {
        return cenarios.salvar(cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo())).enviarACuradoria(autor));
    }

    private void inserirDireto(boolean perguntaBloqueada, boolean respostaBloqueada, String resposta, Long metadados) {
        jdbc.update("""
                INSERT INTO pergunta_de_teste (cenario_versao_id, curador_id, pergunta, pergunta_bloqueada,
                                               resposta_bloqueada, resposta, resposta_ia_meta_id)
                VALUES (?, ?, 'Pergunta', ?, ?, ?, ?)""",
                versao, curador, perguntaBloqueada, respostaBloqueada, resposta, metadados);
    }

    private long inserirDevolucao(long versaoId) {
        return jdbc.queryForObject("""
                INSERT INTO devolucao (cenario_versao_id, curador_id, comentario)
                VALUES (?, ?, 'Corrigir') RETURNING id""", Long.class, versaoId, curador);
    }

    private long inserirMetadados() {
        return jdbc.queryForObject("""
                INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt)
                VALUES ('openai', 'modelo', 'personagem-v1') RETURNING id""", Long.class);
    }

    private long inserirUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
    }
}
