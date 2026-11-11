package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * De ponta a ponta: a aprovação e a devolução feitas pelos casos de uso chegam ao log de auditoria
 * (RN-35). Sem {@code @Transactional}, porque o ouvinte só recebe o evento depois do commit, em outra
 * thread.
 *
 * <p>O que este teste confirma não pode ser apagado: a versão aprovada é imutável (RN-08) e o log é
 * append-only (RN-35). Por isso ele roda num contexto só dele, com PostgreSQL e Redis próprios, para
 * que as linhas gravadas não cheguem aos outros testes. A propriedade abaixo só serve para separar
 * esse contexto.
 */
@SpringBootTest(properties = "lab.teste.contexto=curadoria-na-auditoria")
@Import(InfraestruturaDeTeste.class)
class CuradoriaNaAuditoriaTest {

    @Autowired
    private AutoriaDeCenarios autoria;

    @Autowired
    private EnvioACuradoria envio;

    @Autowired
    private AprovacaoDeCenario aprovacao;

    @Autowired
    private DevolucaoDeCenario devolucao;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private String professora;
    private String curadora;
    private long curadoraId;

    @BeforeEach
    void prepararUsuariosECatalogo() {
        professora = UUID.randomUUID() + "@escola.test";
        curadora = UUID.randomUUID() + "@escola.test";
        inserirUsuario(professora);
        curadoraId = inserirUsuario(curadora);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aprovacaoChegaAoLogComOCuradorEAVersao() throws InterruptedException {
        long id = enviado();
        logar(curadora, "ROLE_CURADOR");

        Cenario aprovado = aprovacao.aprovar(id);

        Map<String, Object> linha = aguardarLinha("APROVAR_CENARIO", id);
        assertThat(linha.get("usuario_id")).isEqualTo(curadoraId);
        assertThat(linha.get("detalhes").toString()).isEqualTo("{\"versaoId\": " + aprovado.versao().id() + "}");
        assertThat(((Timestamp) linha.get("ocorrido_em")).toInstant()).isEqualTo(aprovado.versao().aprovacao().em());
    }

    @Test
    void devolucaoChegaAoLogComOComentario() throws InterruptedException {
        long id = enviado();
        logar(curadora, "ROLE_CURADOR");

        devolucao.devolver(id, "Falta a fonte do voo.");

        Map<String, Object> linha = aguardarLinha("DEVOLVER_CENARIO", id);
        assertThat(linha.get("usuario_id")).isEqualTo(curadoraId);
        assertThat(linha.get("detalhes").toString()).contains("\"comentario\": \"Falta a fonte do voo.\"");
    }

    private long enviado() {
        logar(professora, "ROLE_PROFESSOR");
        long id = autoria.criar(ConteudosDeTeste.completo()).id();
        envio.enviar(id);
        return id;
    }

    /** O ouvinte roda em outra thread depois do commit; espera até 5 segundos. */
    private Map<String, Object> aguardarLinha(String acao, long cenarioId) throws InterruptedException {
        List<Map<String, Object>> linhas = List.of();
        for (int tentativa = 0; tentativa < 100 && linhas.isEmpty(); tentativa++) {
            Thread.sleep(50);
            linhas = jdbc.queryForList("""
                    SELECT usuario_id, detalhes::text AS detalhes, ocorrido_em
                    FROM log_auditoria WHERE acao = ? AND entidade = 'cenario' AND entidade_id = ?""",
                    acao, String.valueOf(cenarioId));
        }
        assertThat(linhas).as("linha %s do log", acao).hasSize(1);
        return linhas.get(0);
    }

    private long inserirUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
    }

    private static void logar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }
}
