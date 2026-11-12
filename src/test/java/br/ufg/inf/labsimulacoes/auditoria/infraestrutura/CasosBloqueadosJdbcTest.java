package br.ufg.inf.labsimulacoes.auditoria.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.auditoria.dominio.CasoBloqueado;
import br.ufg.inf.labsimulacoes.auditoria.dominio.CasosBloqueados;
import br.ufg.inf.labsimulacoes.eventos.ConteudoBloqueado;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Casos de conteúdo bloqueado no PostgreSQL real (RN-20). Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class CasosBloqueadosJdbcTest {

    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private CasosBloqueados casos;

    @Autowired
    private JdbcTemplate jdbc;

    private long sessao;
    private long versao;

    @BeforeEach
    void prepararSessao() {
        var dados = new DadosDeSessao(jdbc);
        long professor = dados.idDe(dados.usuario("PROFESSOR"));
        versao = dados.cenarioAprovado(professor, dados.idDe(dados.usuario("CURADOR"))).versaoId();
        sessao = jdbc.queryForObject(
                """
                INSERT INTO sessao (professor_id, turma_id, cenario_versao_id, limite_perguntas, status, motivo_pausa,
                                    iniciada_em)
                VALUES (?, ?, ?, 40, 'PAUSADA', 'CONTEUDO_BLOQUEADO', now()) RETURNING id""",
                Long.class,
                professor,
                dados.turma(),
                versao);
    }

    @Test
    void casoDaRespostaEntraAbertoComOsTextosEOsMetadados() {
        long meta = jdbc.queryForObject(
                "INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt) VALUES ('openai', 'm', 'v1') RETURNING id",
                Long.class);

        assertThat(casos.abrir(new CasoBloqueado(
                        sessao, versao, ConteudoBloqueado.Origem.RESPOSTA, "Quem é você?", "Frase bloqueada.",
                        List.of("violence", "hate"), meta, AGORA)))
                .isTrue();

        Map<String, Object> linha = linha();
        assertThat(linha)
                .containsEntry("cenario_versao_id", versao)
                .containsEntry("origem", "RESPOSTA")
                .containsEntry("pergunta", "Quem é você?")
                .containsEntry("trecho_bloqueado", "Frase bloqueada.")
                .containsEntry("resposta_ia_meta_id", meta)
                .containsEntry("status", "ABERTO");
        assertThat(categorias(linha)).containsExactly("violence", "hate");
        assertThat(jdbc.queryForObject("SELECT ocorrido_em = ? FROM caso_bloqueado WHERE sessao_id = ?",
                        Boolean.class, AGORA.atOffset(java.time.ZoneOffset.UTC), sessao))
                .isTrue();
    }

    @Test
    void casoDaPerguntaNaoTemTrechoNemMetadados() {
        casos.abrir(new CasoBloqueado(
                sessao, versao, ConteudoBloqueado.Origem.PERGUNTA, "Pergunta imprópria", null, List.of(), null, AGORA));

        Map<String, Object> linha = linha();
        assertThat(linha)
                .containsEntry("origem", "PERGUNTA")
                .containsEntry("trecho_bloqueado", null)
                .containsEntry("resposta_ia_meta_id", null);
        assertThat(categorias(linha)).isEmpty();
    }

    /** Um evento reprocessado depois de um reinício não abre o caso duas vezes. */
    @Test
    void mesmoBloqueioNaoAbreDoisCasos() {
        var caso = new CasoBloqueado(
                sessao, versao, ConteudoBloqueado.Origem.PERGUNTA, "Pergunta imprópria", null, List.of("hate"), null, AGORA);

        assertThat(casos.abrir(caso)).isTrue();
        assertThat(casos.abrir(caso)).isFalse();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM caso_bloqueado WHERE sessao_id = ?", Integer.class, sessao))
                .isEqualTo(1);
    }

    private Map<String, Object> linha() {
        return jdbc.queryForMap("SELECT * FROM caso_bloqueado WHERE sessao_id = ?", sessao);
    }

    private static List<String> categorias(Map<String, Object> linha) {
        try {
            return List.of((String[]) ((Array) linha.get("categorias")).getArray());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
