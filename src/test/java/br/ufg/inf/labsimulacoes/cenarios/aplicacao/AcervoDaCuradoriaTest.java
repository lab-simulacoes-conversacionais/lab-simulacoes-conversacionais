package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TrechoDoAcervo;
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
 * Acervo do curador pelos casos de uso, com perfis e PostgreSQL reais (RN-37). Os cenários passam
 * pelo fluxo da curadoria de verdade. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class AcervoDaCuradoriaTest {

    private static final String PROFESSORA = "professora-acervo@escola.test";
    private static final String OUTRA_PROFESSORA = "outra-professora-acervo@escola.test";
    private static final String CURADORA = "curadora-acervo@escola.test";
    private static final String ADMINISTRADORA = "admin-acervo@escola.test";

    @Autowired
    private AcervoDaCuradoria acervo;

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

    /** RN-37: o curador vê todos os cenários, de todos os autores e em qualquer status. */
    @Test
    void curadorVeTodosOsCenarios() {
        long rascunho = rascunho(PROFESSORA);
        long emCuradoria = enviado(OUTRA_PROFESSORA);
        long devolvido = enviado(PROFESSORA);
        long aprovado = enviado(OUTRA_PROFESSORA);
        logar(CURADORA, "ROLE_CURADOR");
        devolucao.devolver(devolvido, "Corrigir");
        aprovacao.aprovar(aprovado);

        TrechoDoAcervo trecho = acervo.listar(0, 20);

        assertThat(trecho.itens()).extracting(Cenario::id)
                .containsExactlyInAnyOrder(rascunho, emCuradoria, devolvido, aprovado);
        assertThat(trecho.itens()).extracting(Cenario::status).containsExactlyInAnyOrder(
                StatusDoCenario.RASCUNHO, StatusDoCenario.EM_CURADORIA,
                StatusDoCenario.RASCUNHO, StatusDoCenario.APROVADO);
        assertThat(trecho.temProxima()).isFalse();
    }

    /** RF-08: o curador acha o que espera aprovação ou devolução. */
    @Test
    void curadorFiltraOQueEstaEmCuradoria() {
        rascunho(PROFESSORA);
        long primeiro = enviado(PROFESSORA);
        long segundo = enviado(OUTRA_PROFESSORA);

        logar(CURADORA, "ROLE_CURADOR");

        assertThat(acervo.listar(StatusDoCenario.EM_CURADORIA, 0, 20).itens()).extracting(Cenario::id)
                .containsExactlyInAnyOrder(primeiro, segundo);
    }

    @Test
    void devolvidoTrazOComentarioParaOCurador() {
        long devolvido = enviado(PROFESSORA);
        logar(CURADORA, "ROLE_CURADOR");
        devolucao.devolver(devolvido, "Falta a fonte");

        Cenario item = acervo.listar(0, 20).itens().get(0);

        assertThat(item.ultimaDevolucao().comentario()).isEqualTo("Falta a fonte");
    }

    @Test
    void quemTemOsDoisPerfisVeOAcervo() {
        long id = rascunho(OUTRA_PROFESSORA);

        logar(PROFESSORA, "ROLE_PROFESSOR", "ROLE_CURADOR");

        assertThat(acervo.listar(0, 20).itens()).extracting(Cenario::id).containsExactly(id);
    }

    /** RN-37: o professor vê só os próprios cenários, não o acervo. */
    @Test
    void professorNaoVeOAcervo() {
        logar(PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> acervo.listar(0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> acervo.listar(StatusDoCenario.EM_CURADORIA, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** RN-37: o administrador não usa conteúdo pedagógico. */
    @Test
    void administradorNaoVeOAcervo() {
        logar(ADMINISTRADORA, "ROLE_ADMINISTRADOR");

        assertThatThrownBy(() -> acervo.listar(0, 20)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semLoginOAcervoERecusado() {
        assertThatThrownBy(() -> acervo.listar(0, 20)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 101", "-1, 20"})
    void paginaInvalidaERecusada(int pagina, int tamanho) {
        logar(CURADORA, "ROLE_CURADOR");

        assertThatThrownBy(() -> acervo.listar(pagina, tamanho)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filtroSemStatusERecusado() {
        logar(CURADORA, "ROLE_CURADOR");

        assertThatThrownBy(() -> acervo.listar(null, 0, 20)).isInstanceOf(NullPointerException.class);
    }

    private long rascunho(String autora) {
        logar(autora, "ROLE_PROFESSOR");
        return autoria.criar(ConteudosDeTeste.completo()).id();
    }

    private long enviado(String autora) {
        long id = rascunho(autora);
        envio.enviar(id);
        return id;
    }

    private static void logar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }
}
