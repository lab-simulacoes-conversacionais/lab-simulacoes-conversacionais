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
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste.Situacao;
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

/**
 * Perguntas de teste do curador no PostgreSQL real (RF-09, RN-06, RNF-13): a reserva, o resultado na
 * mesma linha, as duas contagens e o fechamento da rodada. Cada teste desfaz o que gravou.
 */
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

    @BeforeEach
    void prepararVersaoEmCuradoria() {
        autor = inserirUsuario("autora-teste-jdbc@escola.test");
        curador = inserirUsuario("curadora-teste-jdbc@escola.test");
        outroCurador = inserirUsuario("curador-teste-jdbc@escola.test");
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
        versao = emCuradoria().versao().id();
    }

    @Test
    void reservaFicaPendenteNaRodadaEmAberto() {
        PerguntaDeTeste reservada = reservar(versao, curador, "Oi?");

        assertThat(reservada.id()).isNotNull();
        assertThat(perguntas.daRodada(versao, curador)).containsExactly(reservada);
        assertThat(jdbc.queryForObject("SELECT situacao FROM pergunta_de_teste WHERE id = ?", String.class, reservada.id()))
                .isEqualTo("PENDENTE");
    }

    @Test
    void resultadoEGravadoNaMesmaLinhaDaReserva() {
        PerguntaDeTeste reservada = reservar(versao, curador, "Oi?");
        long meta = inserirMetadados();

        PerguntaDeTeste concluida = perguntas.concluir(reservada.concluida("Olá!", meta));

        assertThat(concluida).isEqualTo(reservada.concluida("Olá!", meta));
        assertThat(perguntas.daRodada(versao, curador)).containsExactly(concluida);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pergunta_de_teste WHERE cenario_versao_id = ?",
                Integer.class, versao)).isEqualTo(1);
    }

    @Test
    void rodadaVemNaOrdemDeReservaComCadaSituacao() {
        PerguntaDeTeste primeira = perguntas.concluir(reservar(versao, curador, "1").concluida("R", inserirMetadados()));
        PerguntaDeTeste segunda = perguntas.concluir(reservar(versao, curador, "2").comPerguntaBloqueada());
        PerguntaDeTeste terceira = perguntas.concluir(reservar(versao, curador, "3").comRespostaBloqueada(inserirMetadados()));
        PerguntaDeTeste quarta = perguntas.concluir(reservar(versao, curador, "4").comFalha(null));
        PerguntaDeTeste quinta = reservar(versao, curador, "5");

        assertThat(perguntas.daRodada(versao, curador)).containsExactly(primeira, segunda, terceira, quarta, quinta);
    }

    /**
     * As duas contagens: o teto conta todas as perguntas da rodada; o RN-06, só as concluídas e as de
     * resposta bloqueada. Outro curador e outra versão ficam de fora das duas.
     */
    @Test
    void tetoContaTudoEORn06SoAsQueTestaramOPersonagem() {
        perguntas.concluir(reservar(versao, curador, "1").concluida("R", inserirMetadados()));
        perguntas.concluir(reservar(versao, curador, "2").comRespostaBloqueada(inserirMetadados()));
        perguntas.concluir(reservar(versao, curador, "3").comPerguntaBloqueada());
        perguntas.concluir(reservar(versao, curador, "4").comFalha(inserirMetadados()));
        reservar(versao, curador, "5");
        perguntas.concluir(reservar(versao, outroCurador, "6").concluida("R", inserirMetadados()));
        long outraVersao = emCuradoria().versao().id();
        perguntas.concluir(reservar(outraVersao, curador, "7").concluida("R", inserirMetadados()));

        assertThat(perguntas.ocupadas(versao, curador)).isEqualTo(5);
        assertThat(perguntas.contar(versao, curador)).isEqualTo(2);
        assertThat(perguntas.ocupadas(versao, outroCurador)).isEqualTo(1);
        assertThat(perguntas.contar(outraVersao, curador)).isEqualTo(1);
    }

    @Test
    void semPerguntasAsContagensSaoZero() {
        assertThat(perguntas.ocupadas(versao, curador)).isZero();
        assertThat(perguntas.contar(versao, curador)).isZero();
        assertThat(perguntas.daRodada(versao, curador)).isEmpty();
    }

    /** A devolução fecha a rodada de todos os curadores da versão, inclusive as pendentes. */
    @Test
    void fecharRodadaFechaTodasInclusiveAsPendentes() {
        perguntas.concluir(reservar(versao, curador, "1").concluida("R", inserirMetadados()));
        reservar(versao, curador, "2");
        perguntas.concluir(reservar(versao, outroCurador, "3").concluida("R", inserirMetadados()));
        long outraVersao = emCuradoria().versao().id();
        perguntas.concluir(reservar(outraVersao, curador, "4").concluida("R", inserirMetadados()));

        int fechadas = perguntas.fecharRodada(versao, inserirDevolucao(versao));

        assertThat(fechadas).isEqualTo(3);
        assertThat(perguntas.ocupadas(versao, curador)).isZero();
        assertThat(perguntas.contar(versao, curador)).isZero();
        assertThat(perguntas.ocupadas(versao, outroCurador)).isZero();
        assertThat(perguntas.daRodada(versao, curador)).isEmpty();
        assertThat(perguntas.contar(outraVersao, curador)).isEqualTo(1);
    }

    /**
     * Uma pendente fechada pela devolução e concluída depois continua fechada: não conta nem ocupa vaga
     * na rodada nova.
     */
    @Test
    void pendenteFechadaPelaDevolucaoContinuaFechadaDepoisDeConcluida() {
        PerguntaDeTeste reservada = reservar(versao, curador, "Oi?");
        perguntas.fecharRodada(versao, inserirDevolucao(versao));

        PerguntaDeTeste concluida = perguntas.concluir(reservada.concluida("Olá!", inserirMetadados()));

        assertThat(concluida.rodadaFechada()).isTrue();
        assertThat(concluida.conta()).isFalse();
        assertThat(perguntas.ocupadas(versao, curador)).isZero();
        assertThat(perguntas.contar(versao, curador)).isZero();
    }

    /** Uma rodada fechada não é reaberta: a segunda devolução só fecha as perguntas novas. */
    @Test
    void segundaDevolucaoFechaSoARodadaNova() {
        perguntas.concluir(reservar(versao, curador, "1").concluida("R", inserirMetadados()));
        long primeira = inserirDevolucao(versao);
        perguntas.fecharRodada(versao, primeira);
        perguntas.concluir(reservar(versao, curador, "2").concluida("R", inserirMetadados()));

        assertThat(perguntas.contar(versao, curador)).isEqualTo(1);
        assertThat(perguntas.fecharRodada(versao, inserirDevolucao(versao))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pergunta_de_teste WHERE devolucao_id = ?", Integer.class, primeira))
                .isEqualTo(1);
    }

    /** O resultado vai uma vez só: a pergunta que já tem resultado não muda. */
    @Test
    void perguntaComResultadoNaoEConcluidaDeNovo() {
        PerguntaDeTeste reservada = reservar(versao, curador, "Oi?");
        perguntas.concluir(reservada.comPerguntaBloqueada());

        assertThatThrownBy(() -> perguntas.concluir(reservada.concluida("Olá!", inserirMetadados())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("já tem resultado");
        assertThat(perguntas.daRodada(versao, curador).getFirst().situacao()).isEqualTo(Situacao.PERGUNTA_BLOQUEADA);
    }

    /** O banco também recusa uma situação incoerente, além do domínio. Um caso por vez: o erro aborta a transação. */
    @ParameterizedTest(name = "situação={0}, resposta=[{1}], com metadados={2}")
    @CsvSource({
        "PENDENTE,           Resposta, false",
        "PENDENTE,                   , true",
        "PERGUNTA_BLOQUEADA,         , true",
        "PERGUNTA_BLOQUEADA, Resposta, false",
        "RESPOSTA_BLOQUEADA, Resposta, true",
        "FALHA,              Resposta, true",
        "CONCLUIDA,                  , true",
        "CONCLUIDA,          '  ',     true",
        "DESCONHECIDA,               , false"
    })
    void bancoRecusaSituacaoIncoerente(String situacao, String resposta, boolean comMetadados) {
        assertThatThrownBy(() -> inserirDireto(situacao, resposta, comMetadados ? inserirMetadados() : null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "situação={0}, resposta=[{1}], com metadados={2}")
    @CsvSource({
        "PENDENTE,                   , false",
        "PERGUNTA_BLOQUEADA,         , false",
        "RESPOSTA_BLOQUEADA,         , true",
        "FALHA,                      , true",
        "FALHA,                      , false",
        "CONCLUIDA,          Resposta, true",
        "CONCLUIDA,          Resposta, false"
    })
    void bancoAceitaAsSituacoesCoerentes(String situacao, String resposta, boolean comMetadados) {
        assertThatCode(() -> inserirDireto(situacao, resposta, comMetadados ? inserirMetadados() : null))
                .doesNotThrowAnyException();
    }

    /** O mesmo limite do domínio, para uma imagem ou um script que grave direto no banco. */
    @Test
    void bancoRecusaPerguntaAcimaDe1000Caracteres() {
        assertThatCode(() -> jdbc.update("""
                INSERT INTO pergunta_de_teste (cenario_versao_id, curador_id, pergunta) VALUES (?, ?, ?)""",
                versao, curador, "a".repeat(1000))).doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO pergunta_de_teste (cenario_versao_id, curador_id, pergunta) VALUES (?, ?, ?)""",
                versao, curador, "a".repeat(1001))).isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Cada resposta da IA pertence a uma pergunta de teste só; várias sem metadados podem existir. */
    @Test
    void osMesmosMetadadosNaoServemADuasPerguntas() {
        long meta = inserirMetadados();
        inserirDireto("CONCLUIDA", "Resposta", meta);
        inserirDireto("FALHA", null, null);
        inserirDireto("FALHA", null, null);

        assertThatThrownBy(() -> inserirDireto("FALHA", null, meta)).isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Como na mensagem da sala, apagar os metadados não apaga a pergunta de teste. */
    @Test
    void apagarOsMetadadosMantemAPergunta() {
        long meta = inserirMetadados();
        PerguntaDeTeste concluida = perguntas.concluir(reservar(versao, curador, "Oi?").concluida("Olá!", meta));

        jdbc.update("DELETE FROM resposta_ia_meta WHERE id = ?", meta);

        assertThat(perguntas.daRodada(versao, curador)).containsExactly(new PerguntaDeTeste(
                concluida.id(), versao, curador, "Oi?", Situacao.CONCLUIDA, "Olá!", null, false));
    }

    /**
     * RNF-12: a imagem anterior, que não conhece pergunta_de_teste, continua devolvendo e aprovando uma
     * versão que já tem perguntas de teste, inclusive pendentes. Os comandos são os do CenariosJdbc daquela
     * imagem. A devolução feita por ela não fecha a rodada; depois de voltar a esta imagem, aquelas
     * perguntas continuam na rodada, o que só pode adiantar o checklist, nunca impedir uma aprovação.
     */
    @Test
    void imagemAnteriorDevolveEAprovaUmaVersaoComPerguntasDeTeste() {
        perguntas.concluir(reservar(versao, curador, "Oi?").concluida("Olá!", inserirMetadados()));
        reservar(versao, curador, "Pendente");

        assertThatCode(() -> {
            jdbc.update("INSERT INTO devolucao (cenario_versao_id, curador_id, comentario, criado_em) "
                    + "VALUES (?, ?, 'Corrigir', now())", versao, curador);
            jdbc.update("UPDATE cenario_versao SET status = 'RASCUNHO' WHERE id = ?", versao);
            jdbc.update("UPDATE cenario_versao SET status = 'EM_CURADORIA' WHERE id = ?", versao);
            jdbc.update("UPDATE cenario_versao SET status = 'APROVADA', aprovado_por_id = ?, aprovado_em = now() "
                    + "WHERE id = ?", curador, versao);
        }).doesNotThrowAnyException();
        assertThat(perguntas.contar(versao, curador)).isEqualTo(1);
        assertThat(perguntas.ocupadas(versao, curador)).isEqualTo(2);
    }

    private PerguntaDeTeste reservar(long versaoId, long curadorId, String pergunta) {
        return perguntas.reservar(PerguntaDeTeste.reservada(versaoId, curadorId, pergunta));
    }

    private Cenario emCuradoria() {
        return cenarios.salvar(cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo())).enviarACuradoria(autor));
    }

    private void inserirDireto(String situacao, String resposta, Long metadados) {
        jdbc.update("""
                INSERT INTO pergunta_de_teste (cenario_versao_id, curador_id, pergunta, situacao, resposta,
                                               resposta_ia_meta_id)
                VALUES (?, ?, 'Pergunta', ?, ?, ?)""",
                versao, curador, situacao, resposta, metadados);
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
