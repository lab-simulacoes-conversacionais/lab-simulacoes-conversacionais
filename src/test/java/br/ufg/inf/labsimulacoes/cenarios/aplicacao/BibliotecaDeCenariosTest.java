package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.BibliotecaDeCenarios;
import br.ufg.inf.labsimulacoes.cenarios.CenarioDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.PaginaDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Pagina;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Biblioteca pelos casos de uso, com perfis e PostgreSQL reais (RF-12, RN-37). Os cenários passam
 * pelo fluxo da curadoria de verdade. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class BibliotecaDeCenariosTest {

    private static final String PROFESSORA = "professora-biblioteca@escola.test";
    private static final String OUTRA_PROFESSORA = "outra-professora-biblioteca@escola.test";
    private static final String CURADORA = "curadora-biblioteca@escola.test";
    private static final String ADMINISTRADORA = "admin-biblioteca@escola.test";

    @Autowired
    private BibliotecaDeCenarios biblioteca;

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

    @BeforeEach
    void prepararUsuariosECatalogo() {
        for (String email : List.of(PROFESSORA, OUTRA_PROFESSORA, CURADORA, ADMINISTRADORA)) {
            jdbc.update("INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash')", email);
        }
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    /** RN-37: o professor vê a biblioteca aprovada da escola, inclusive o que outros criaram. */
    @Test
    void professorVeOsAprovadosDeTodosOsAutores() {
        Cenario proprio = aprovado(PROFESSORA);
        Cenario deOutra = aprovado(OUTRA_PROFESSORA);

        logar(PROFESSORA, "ROLE_PROFESSOR");
        PaginaDaBiblioteca pagina = biblioteca.listar(0, 20);

        assertThat(pagina.cenarios()).extracting(CenarioDaBiblioteca::cenarioId)
                .containsExactly(proprio.id(), deOutra.id());
        assertThat(pagina.pagina()).isZero();
        assertThat(pagina.tamanho()).isEqualTo(20);
        assertThat(pagina.temProxima()).isFalse();
    }

    @Test
    void curadorVeAMesmaBiblioteca() {
        Cenario aprovado = aprovado(PROFESSORA);

        logar(CURADORA, "ROLE_CURADOR");

        assertThat(biblioteca.listar(0, 20).cenarios()).extracting(CenarioDaBiblioteca::cenarioId)
                .containsExactly(aprovado.id());
    }

    /** RN-01: o que não foi aprovado, ou voltou para rascunho, não entra, nem para o curador. */
    @Test
    void naoAprovadosFicamDeFora() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        autoria.criar(ConteudosDeTeste.completo());
        long emCuradoria = autoria.criar(ConteudosDeTeste.completo()).id();
        envio.enviar(emCuradoria);
        long devolvido = autoria.criar(ConteudosDeTeste.completo()).id();
        envio.enviar(devolvido);
        logar(CURADORA, "ROLE_CURADOR");
        devolucao.devolver(devolvido, "Corrigir");
        Cenario aprovado = aprovado(OUTRA_PROFESSORA);

        logar(CURADORA, "ROLE_CURADOR");

        assertThat(biblioteca.listar(0, 20).cenarios()).extracting(CenarioDaBiblioteca::cenarioId)
                .containsExactly(aprovado.id());
    }

    @Test
    void itemTrazOResumoDaVersaoVigente() {
        Cenario aprovado = aprovado(PROFESSORA);

        logar(PROFESSORA, "ROLE_PROFESSOR");
        CenarioDaBiblioteca item = biblioteca.listar(0, 20).cenarios().get(0);

        assertThat(item).isEqualTo(new CenarioDaBiblioteca(
                aprovado.id(),
                aprovado.versao().id(),
                1,
                "O voo do 14-bis",
                "História",
                "EF",
                8,
                List.of("EF08HI03"),
                "13 a 14 anos",
                20,
                aprovado.versao().aprovacao().em()));
    }

    @Test
    void bibliotecaVazia() {
        logar(PROFESSORA, "ROLE_PROFESSOR");

        PaginaDaBiblioteca pagina = biblioteca.listar(0, 20);

        assertThat(pagina.cenarios()).isEmpty();
        assertThat(pagina.temProxima()).isFalse();
    }

    @Test
    void paginacaoDivideABiblioteca() {
        List<Long> ids = List.of(aprovado(PROFESSORA).id(), aprovado(PROFESSORA).id(), aprovado(PROFESSORA).id());

        logar(PROFESSORA, "ROLE_PROFESSOR");
        PaginaDaBiblioteca primeira = biblioteca.listar(0, 2);
        PaginaDaBiblioteca segunda = biblioteca.listar(1, 2);

        assertThat(primeira.cenarios()).extracting(CenarioDaBiblioteca::cenarioId).containsExactly(ids.get(0), ids.get(1));
        assertThat(primeira.temProxima()).isTrue();
        assertThat(segunda.cenarios()).extracting(CenarioDaBiblioteca::cenarioId).containsExactly(ids.get(2));
        assertThat(segunda.temProxima()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 101", "-1, 20"})
    void paginaInvalidaERecusada(int pagina, int tamanho) {
        logar(PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> biblioteca.listar(pagina, tamanho)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limiteDaApiEOMesmoDoDominio() {
        assertThat(BibliotecaDeCenarios.TAMANHO_MAXIMO_DA_PAGINA).isEqualTo(Pagina.TAMANHO_MAXIMO);
        logar(PROFESSORA, "ROLE_PROFESSOR");
        assertThat(biblioteca.listar(0, BibliotecaDeCenarios.TAMANHO_MAXIMO_DA_PAGINA).tamanho()).isEqualTo(100);
    }

    /** RN-37: o administrador não usa conteúdo pedagógico. */
    @Test
    void administradorNaoVeABiblioteca() {
        logar(ADMINISTRADORA, "ROLE_ADMINISTRADOR");

        assertThatThrownBy(() -> biblioteca.listar(0, 20)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semLoginABibliotecaERecusada() {
        assertThatThrownBy(() -> biblioteca.listar(0, 20)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private Cenario aprovado(String autora) {
        logar(autora, "ROLE_PROFESSOR");
        long id = autoria.criar(ConteudosDeTeste.completo()).id();
        envio.enviar(id);
        logar(CURADORA, "ROLE_CURADOR");
        return aprovacao.aprovar(id);
    }

    private static void logar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }
}
