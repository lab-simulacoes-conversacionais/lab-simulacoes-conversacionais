package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.BibliotecaDeCenarios;
import br.ufg.inf.labsimulacoes.cenarios.CenarioDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ArquivamentoNaoPermitido;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.eventos.CenarioArquivado;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arquivamento com perfis e PostgreSQL reais (RF-13, RN-09). O cenário passa pela curadoria de verdade
 * antes de ser arquivado. Cada teste desfaz o que gravou; o log de auditoria que recebe o evento
 * depois do commit é conferido no {@link CuradoriaNaAuditoriaTest}.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
@RecordApplicationEvents
class ArquivamentoDeCenarioTest {

    private static final String PROFESSORA = "professora-arquivamento@escola.test";
    private static final String OUTRA_PROFESSORA = "outra-arquivamento@escola.test";
    private static final String CURADORA = "curadora-arquivamento@escola.test";
    private static final String ADMINISTRADORA = "admin-arquivamento@escola.test";

    @Autowired
    private ArquivamentoDeCenario arquivamento;

    @Autowired
    private AutoriaDeCenarios autoria;

    @Autowired
    private EnvioACuradoria envio;

    @Autowired
    private AprovacaoDeCenario aprovacao;

    @Autowired
    private AcervoDaCuradoria acervo;

    @Autowired
    private BibliotecaDeCenarios biblioteca;

    @Autowired
    private CenariosAprovados aprovados;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents eventos;

    private long curadora;

    @BeforeEach
    void prepararUsuariosECatalogo() {
        inserirUsuario(PROFESSORA);
        inserirUsuario(OUTRA_PROFESSORA);
        curadora = inserirUsuario(CURADORA);
        inserirUsuario(ADMINISTRADORA);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void curadorArquivaEPublicaOEvento() {
        Cenario aprovado = aprovado();
        logar(CURADORA, "ROLE_CURADOR");

        Cenario arquivado = arquivamento.arquivar(aprovado.id());

        assertThat(arquivado.status()).isEqualTo(StatusDoCenario.ARQUIVADO);
        assertThat(eventos.stream(CenarioArquivado.class))
                .singleElement()
                .satisfies(evento -> {
                    assertThat(evento.cenarioId()).isEqualTo(aprovado.id());
                    assertThat(evento.versaoId()).isEqualTo(aprovado.versao().id());
                    assertThat(evento.arquivadoPorId()).isEqualTo(curadora);
                    assertThat(evento.ocorridoEm()).isNotNull();
                });
    }

    /** RN-09: sai da biblioteca e não vai mais para a sala. */
    @Test
    void arquivadoSaiDaBibliotecaENaoTemVersaoVigente() {
        Cenario aprovado = aprovado();
        logar(CURADORA, "ROLE_CURADOR");
        arquivamento.arquivar(aprovado.id());

        logar(OUTRA_PROFESSORA, "ROLE_PROFESSOR");
        assertThat(biblioteca.listar(0, 10).cenarios()).extracting(CenarioDaBiblioteca::cenarioId)
                .doesNotContain(aprovado.id());
        assertThat(aprovados.versaoVigente(aprovado.id())).isEmpty();
    }

    /**
     * RN-09: o histórico continua consultável. A sessão que usou a versão ainda a lê pelo id, o autor
     * ainda vê o cenário e o curador o acha no acervo.
     */
    @Test
    void historicoContinuaDisponivel() {
        Cenario aprovado = aprovado();
        long versaoId = aprovado.versao().id();
        logar(CURADORA, "ROLE_CURADOR");
        arquivamento.arquivar(aprovado.id());

        assertThat(acervo.listar(StatusDoCenario.ARQUIVADO, 0, 10).itens()).extracting(Cenario::id)
                .contains(aprovado.id());
        logar(PROFESSORA, "ROLE_PROFESSOR");
        Cenario lido = autoria.buscar(aprovado.id());
        assertThat(lido.status()).isEqualTo(StatusDoCenario.ARQUIVADO);
        assertThat(lido.versao().id()).isEqualTo(versaoId);
        assertThat(lido.versao().status()).isEqualTo(StatusDaVersao.APROVADA);
        assertThat(aprovados.versaoAprovada(versaoId)).isPresent();
    }

    @Test
    void rascunhoNaoEArquivadoENadaMuda() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        long id = autoria.criar(ConteudosDeTeste.completo()).id();
        logar(CURADORA, "ROLE_CURADOR");

        assertThatThrownBy(() -> arquivamento.arquivar(id)).isInstanceOf(ArquivamentoNaoPermitido.class);
        assertThat(acervo.listar(0, 10).itens()).filteredOn(cenario -> cenario.id() == id)
                .extracting(Cenario::status)
                .containsExactly(StatusDoCenario.RASCUNHO);
        assertThat(eventos.stream(CenarioArquivado.class)).isEmpty();
    }

    @Test
    void arquivadoNaoEArquivadoDeNovo() {
        Cenario aprovado = aprovado();
        logar(CURADORA, "ROLE_CURADOR");
        arquivamento.arquivar(aprovado.id());

        assertThatThrownBy(() -> arquivamento.arquivar(aprovado.id())).isInstanceOf(ArquivamentoNaoPermitido.class);
        assertThat(eventos.stream(CenarioArquivado.class)).hasSize(1);
    }

    /** RN-37: nem o autor, se for só professor, nem o administrador arquivam. */
    @Test
    void professorEAdministradorNaoArquivam() {
        Cenario aprovado = aprovado();

        logar(PROFESSORA, "ROLE_PROFESSOR");
        assertThatThrownBy(() -> arquivamento.arquivar(aprovado.id())).isInstanceOf(AccessDeniedException.class);
        logar(ADMINISTRADORA, "ROLE_ADMINISTRADOR");
        assertThatThrownBy(() -> arquivamento.arquivar(aprovado.id())).isInstanceOf(AccessDeniedException.class);
        assertThat(eventos.stream(CenarioArquivado.class)).isEmpty();
    }

    @Test
    void cenarioInexistenteERecusado() {
        logar(CURADORA, "ROLE_CURADOR");

        assertThatThrownBy(() -> arquivamento.arquivar(-1)).isInstanceOf(CenarioNaoEncontrado.class);
    }

    private Cenario aprovado() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        long id = autoria.criar(ConteudosDeTeste.completo()).id();
        envio.enviar(id);
        logar(CURADORA, "ROLE_CURADOR");
        return aprovacao.aprovar(id);
    }

    private long inserirUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
    }

    private static void logar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }
}
