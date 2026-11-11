package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioForaDeRascunho;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadesForaDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Criação manual de cenários, com perfis e PostgreSQL reais (RF-06). Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class AutoriaDeCenariosTest {

    private static final String PROFESSORA = "professora-autoria@escola.test";
    private static final String OUTRA_PROFESSORA = "outra-autoria@escola.test";
    private static final String CURADORA = "curadora-autoria@escola.test";

    @Autowired
    private AutoriaDeCenarios autoria;

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private long professora;

    @BeforeEach
    void prepararUsuariosECatalogo() {
        professora = inserirUsuario(PROFESSORA);
        inserirUsuario(OUTRA_PROFESSORA);
        inserirUsuario(CURADORA);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void professorCriaRascunhoComoAutor() {
        logar(PROFESSORA, "ROLE_PROFESSOR");

        Cenario criado = autoria.criar(ConteudosDeTeste.completo());

        assertThat(criado.autorId()).isEqualTo(professora);
        assertThat(criado.status()).isEqualTo(StatusDoCenario.RASCUNHO);
        assertThat(autoria.buscar(criado.id())).isEqualTo(criado);
    }

    @Test
    void curadorTambemCriaCenario() {
        logar(CURADORA, "ROLE_CURADOR");

        assertThat(autoria.criar(ConteudosDeTeste.soComTitulo()).id()).isNotNull();
    }

    @Test
    void rascunhoPodeNascerIncompleto() {
        logar(PROFESSORA, "ROLE_PROFESSOR");

        Cenario criado = autoria.criar(ConteudosDeTeste.soComTitulo());

        assertThat(criado.versao().conteudo().pendencias()).isNotEmpty();
    }

    @Test
    void habilidadeForaDoCatalogoERecusadaAoCriarDizendoQualE() {
        logar(PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> autoria.criar(comHabilidade("EF01XX99")))
                .isInstanceOf(HabilidadesForaDoCatalogo.class)
                .hasMessageContaining("EF01XX99")
                .hasMessageNotContaining("EF08HI03");
    }

    @Test
    void autorAlteraOProprioRascunho() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        Cenario criado = autoria.criar(ConteudosDeTeste.soComTitulo());

        Cenario alterado = autoria.alterarRascunho(criado.id(), ConteudosDeTeste.completo());

        assertThat(alterado.versao().conteudo()).isEqualTo(ConteudosDeTeste.completo());
        assertThat(autoria.buscar(criado.id())).isEqualTo(alterado);
    }

    @Test
    void habilidadeForaDoCatalogoERecusadaAoAlterar() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        Cenario criado = autoria.criar(ConteudosDeTeste.soComTitulo());

        assertThatThrownBy(() -> autoria.alterarRascunho(criado.id(), comHabilidade("EF01XX99")))
                .isInstanceOf(HabilidadesForaDoCatalogo.class);
    }

    /** Figura 3: quem edita o rascunho é o autor, mesmo que o outro usuário seja curador. */
    @Test
    void quemNaoEAutorNaoAlteraORascunho() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        Cenario criado = autoria.criar(ConteudosDeTeste.soComTitulo());

        logar(CURADORA, "ROLE_CURADOR");

        assertThatThrownBy(() -> autoria.alterarRascunho(criado.id(), ConteudosDeTeste.completo()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void versaoQueSaiuDoRascunhoNaoEAlterada() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        Cenario criado = autoria.criar(ConteudosDeTeste.completo());
        cenarios.salvar(criado.enviarACuradoria(professora));

        assertThatThrownBy(() -> autoria.alterarRascunho(criado.id(), ConteudosDeTeste.soComTitulo()))
                .isInstanceOf(CenarioForaDeRascunho.class);
    }

    /** RN-37: o professor vê só os próprios cenários. */
    @Test
    void professorNaoVeCenarioDeOutroProfessor() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        Cenario criado = autoria.criar(ConteudosDeTeste.soComTitulo());

        logar(OUTRA_PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> autoria.buscar(criado.id())).isInstanceOf(AccessDeniedException.class);
    }

    /** RN-37: o curador vê todos os cenários da escola. */
    @Test
    void curadorVeCenarioDeQualquerAutor() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        Cenario criado = autoria.criar(ConteudosDeTeste.soComTitulo());

        logar(CURADORA, "ROLE_CURADOR");

        assertThat(autoria.buscar(criado.id())).isEqualTo(criado);
    }

    @Test
    void cenarioInexistenteERecusado() {
        logar(PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> autoria.buscar(-1)).isInstanceOf(CenarioNaoEncontrado.class);
        assertThatThrownBy(() -> autoria.alterarRascunho(-1, ConteudosDeTeste.completo()))
                .isInstanceOf(CenarioNaoEncontrado.class);
    }

    /** RN-37: o administrador não edita conteúdo pedagógico. */
    @Test
    void administradorNaoUsaAAutoria() {
        logar(PROFESSORA, "ROLE_ADMINISTRADOR");

        assertThatThrownBy(() -> autoria.criar(ConteudosDeTeste.completo()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> autoria.buscar(1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> autoria.alterarRascunho(1, ConteudosDeTeste.completo()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semLoginAAutoriaERecusada() {
        assertThatThrownBy(() -> autoria.criar(ConteudosDeTeste.completo()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private static ConteudoDoCenario comHabilidade(String... codigos) {
        var c = ConteudosDeTeste.CURRICULO;
        var curriculo = new VinculoCurricular(c.componente(), c.etapa(), c.ano(), List.of(codigos));
        return new ConteudoDoCenario("O voo do 14-bis", ConteudosDeTeste.ROTEIRO, curriculo, "13 a 14 anos", 20);
    }

    private long inserirUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Usuária', ?, 'hash') RETURNING id",
                Long.class,
                email);
    }

    private static void logar(String email, String... papeis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, papeis));
    }
}
