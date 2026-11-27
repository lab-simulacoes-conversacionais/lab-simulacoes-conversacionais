package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.RegistroDeSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.GravidadePercebida;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TipoDeErro;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao.VersaoNoBanco;
import br.ufg.inf.labsimulacoes.sessoes.dominio.CenarioSemVersaoAprovada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Critério de aceite da RF-25 de ponta a ponta, pelos casos de uso reais: a professora sinaliza como grave uma
 * resposta da sessão em andamento (RF-23), Cenários suspende o cenário na hora (RN-23a), Sessões pausa a sessão
 * (RN-25), o Redis mostra a pausa à tela e a suspensão vai para o log (RN-35). Novas sessões com o cenário
 * ficam bloqueadas (RN-25).
 *
 * <p>Sem {@code @Transactional}: cada passo da cadeia roda depois da confirmação do anterior, em outra thread.
 */
@SpringBootTest(properties = "lab.teste.contexto=suspensao")
@Import(InfraestruturaDeTeste.class)
class SuspensaoDoCenarioNaSalaTest {

    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Autowired
    private InicioDeSessao inicio;

    @Autowired
    private RegistroDeSinalizacao registro;

    @Autowired
    private EstadosDasSessoes estados;

    @Autowired
    private JdbcTemplate jdbc;

    private DadosDeSessao dados;
    private String professora;
    private long professoraId;
    private VersaoNoBanco cenario;

    @BeforeEach
    void prepararProfessoraECenario() {
        dados = new DadosDeSessao(jdbc);
        professora = dados.usuario("PROFESSOR");
        professoraId = dados.idDe(professora);
        cenario = dados.cenarioAprovado(professoraId, dados.idDe(dados.usuario("CURADOR")));
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(professora, null, "ROLE_PROFESSOR"));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinalizacaoGraveDaProfessoraSuspendeOCenarioEPausaASessao() {
        long sessao = inicio.iniciar(dados.turma(), cenario.cenarioId()).id();
        long resposta = respostaDaIa(sessao);

        registro.sinalizar(sessao, resposta, TipoDeErro.ERRO_HISTORICO, GravidadePercebida.GRAVE,
                "O primeiro voo homologado foi em 1906, e não em 1901.");

        await().atMost(ESPERA).until(() -> "PAUSADA".equals(
                jdbc.queryForObject("SELECT status FROM sessao WHERE id = ?", String.class, sessao)));
        assertThat(jdbc.queryForObject("SELECT status FROM cenario WHERE id = ?", String.class, cenario.cenarioId()))
                .isEqualTo("SUSPENSO");
        assertThat(jdbc.queryForObject("SELECT motivo_pausa FROM sessao WHERE id = ?", String.class, sessao))
                .isEqualTo("CENARIO_SUSPENSO");
        EstadoDaSessao noRedis = estados.buscar(sessao).orElseThrow();
        assertThat(noRedis.status()).isEqualTo(StatusDaSessao.PAUSADA);
        assertThat(noRedis.motivoDaPausa()).isEqualTo(MotivoDaPausa.CENARIO_SUSPENSO);

        Map<String, Object> linha = linhaDaSuspensao();
        assertThat(linha.get("usuario_id")).isEqualTo(professoraId);
        assertThat(linha.get("detalhes").toString()).contains("\"motivo\": \"SINALIZACAO_GRAVE\"")
                .contains("\"versaoId\": " + cenario.versaoId())
                .doesNotContain("1901", "1906");

        // RN-25: com o cenário suspenso, nenhuma sessão nova começa com ele.
        assertThatThrownBy(() -> inicio.iniciar(dados.turma(), cenario.cenarioId()))
                .isInstanceOf(CenarioSemVersaoAprovada.class);
    }

    /** Uma pergunta da turma e a resposta da IA, com os metadados que a sinalização copia (RNF-10). */
    private long respostaDaIa(long sessao) {
        long meta = jdbc.queryForObject("""
                INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt)
                VALUES ('openai', 'modelo', 'personagem-v1') RETURNING id""", Long.class);
        Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbc.update("INSERT INTO mensagem (sessao_id, autoria, texto, criado_em) VALUES (?, 'TURMA', ?, ?)",
                sessao, "Quando foi o primeiro voo?", agora.atOffset(ZoneOffset.UTC));
        return jdbc.queryForObject("""
                INSERT INTO mensagem (sessao_id, autoria, texto, resposta_ia_meta_id, criado_em)
                VALUES (?, 'IA', ?, ?, ?) RETURNING id""",
                Long.class, sessao, "Voei em 1901, em Paris.", meta, agora.plusSeconds(1).atOffset(ZoneOffset.UTC));
    }

    private Map<String, Object> linhaDaSuspensao() {
        String consulta = """
                SELECT usuario_id, detalhes::text AS detalhes FROM log_auditoria
                WHERE acao = 'SUSPENDER_CENARIO' AND entidade_id = ?""";
        String cenarioId = String.valueOf(cenario.cenarioId());
        await().atMost(ESPERA).until(() -> !jdbc.queryForList(consulta, cenarioId).isEmpty());
        return jdbc.queryForList(consulta, cenarioId).getFirst();
    }
}
