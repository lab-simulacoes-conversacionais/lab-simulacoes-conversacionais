package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadesForaDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PedidoDeSugestaoDeCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RoteiroDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeSugestao;
import br.ufg.inf.labsimulacoes.ia.RoteiroSugerido;
import br.ufg.inf.labsimulacoes.ia.SugestaoDeCenario;
import java.time.Instant;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sugestão de cenário pela IA (RF-07), com perfis e PostgreSQL reais e o adaptador de IA simulado.
 * Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class SugestaoDeCenariosTest {

    private static final String PROFESSORA = "professora-sugestao@escola.test";
    private static final String CURADORA = "curadora-sugestao@escola.test";
    private static final VinculoCurricular CURRICULO = ConteudosDeTeste.CURRICULO;
    private static final PedidoDeSugestaoDeCenario PEDIDO = new PedidoDeSugestaoDeCenario("O voo do 14-bis", CURRICULO);
    private static final RoteiroDoCenario ROTEIRO = ConteudosDeTeste.ROTEIRO;
    private static final RoteiroSugerido ROTEIRO_SUGERIDO = new RoteiroSugerido(
            "Santos Dumont em Paris",
            ROTEIRO.personagem(),
            ROTEIRO.papelDaIa(),
            ROTEIRO.contexto(),
            ROTEIRO.objetivos(),
            ROTEIRO.limitesDeAssunto(),
            ROTEIRO.fontes(),
            ROTEIRO.perguntasDisparadoras(),
            "13 a 14 anos",
            25);

    @MockitoBean
    private AdaptadorDeIa adaptador;

    @Autowired
    private SugestaoDeCenarios sugestao;

    @Autowired
    private AutoriaDeCenarios autoria;

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private long professora;
    private long curadora;
    private long geracao;

    @BeforeEach
    void prepararUsuariosCatalogoEGeracao() {
        professora = inserirUsuario(PROFESSORA);
        curadora = inserirUsuario(CURADORA);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
        geracao = jdbc.queryForObject("""
                INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt)
                VALUES ('openai', 'modelo', 'sugestao-v1') RETURNING id""", Long.class);
        when(adaptador.sugerirCenario(any())).thenReturn(new SugestaoDeCenario(ROTEIRO_SUGERIDO, geracao));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    /** RN-04: critério de aceite da issue. */
    @Test
    void sugestaoEntraComoRascunhoNovoMarcadoSugeridoPorIa() {
        logar(PROFESSORA, "ROLE_PROFESSOR");

        Cenario sugerido = sugestao.sugerir(PEDIDO);

        assertThat(sugerido.id()).isNotNull();
        assertThat(sugerido.status()).isEqualTo(StatusDoCenario.RASCUNHO);
        assertThat(sugerido.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(sugerido.versao().numero()).isEqualTo(1);
        assertThat(sugerido.sugestaoDaIa()).isEqualTo(geracao);
        assertThat(sugerido.autorId()).isEqualTo(professora);
        assertThat(autoria.buscar(sugerido.id())).isEqualTo(sugerido);
    }

    @Test
    void rascunhoTemORoteiroDaIaEOVinculoDoPedido() {
        logar(PROFESSORA, "ROLE_PROFESSOR");

        Cenario sugerido = sugestao.sugerir(PEDIDO);

        assertThat(sugerido.versao().conteudo())
                .isEqualTo(new ConteudoDoCenario("Santos Dumont em Paris", ROTEIRO, CURRICULO, "13 a 14 anos", 25));
    }

    @Test
    void adaptadorRecebeOTemaEOVinculoDoPedido() {
        logar(PROFESSORA, "ROLE_PROFESSOR");

        sugestao.sugerir(PEDIDO);

        verify(adaptador).sugerirCenario(
                new PedidoDeSugestao("O voo do 14-bis", "História", "EF", 8, List.of("EF08HI03")));
    }

    /** O rascunho sugerido segue o caminho de qualquer rascunho: o autor ajusta e envia à curadoria. */
    @Test
    void autorAjustaORascunhoSugeridoSemPerderAMarca() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        Cenario sugerido = sugestao.sugerir(PEDIDO);

        Cenario ajustado = autoria.alterarRascunho(sugerido.id(), ConteudosDeTeste.completo());

        assertThat(ajustado.versao().conteudo()).isEqualTo(ConteudosDeTeste.completo());
        assertThat(autoria.buscar(sugerido.id()).sugestaoDaIa()).isEqualTo(geracao);
    }

    @Test
    void curadorTambemPedeSugestao() {
        logar(CURADORA, "ROLE_CURADOR");

        assertThat(sugestao.sugerir(PEDIDO).autorId()).isEqualTo(curadora);
    }

    /** RN-04: a IA nunca altera um cenário aprovado; a sugestão sempre cria um cenário novo. */
    @Test
    void sugestaoNaoMexeNosCenariosQueJaExistem() {
        Cenario aprovado = cenarios.salvar(cenarios.salvar(Cenario.novo(professora, ConteudosDeTeste.completo())
                        .enviarACuradoria(professora))
                .aprovar(new Revisor(curadora, true), Instant.parse("2026-11-14T13:00:00Z")));
        logar(PROFESSORA, "ROLE_PROFESSOR");

        Cenario sugerido = sugestao.sugerir(PEDIDO);

        assertThat(sugerido.id()).isNotEqualTo(aprovado.id());
        assertThat(cenarios.buscar(aprovado.id())).contains(aprovado);
        assertThat(cenarios.versaoVigente(aprovado.id())).contains(aprovado.versao());
    }

    @Test
    void sugestaoVaziaViraRascunhoComOTemaComoTitulo() {
        var vazio = new RoteiroSugerido(null, null, null, null, null, null, null, null, null, null);
        when(adaptador.sugerirCenario(any())).thenReturn(new SugestaoDeCenario(vazio, geracao));
        logar(PROFESSORA, "ROLE_PROFESSOR");

        Cenario sugerido = sugestao.sugerir(PEDIDO);

        assertThat(sugerido.versao().conteudo().titulo()).isEqualTo("O voo do 14-bis");
        assertThat(sugerido.versao().conteudo().pendencias()).isNotEmpty();
    }

    @Test
    void habilidadeForaDoCatalogoERecusadaSemChamarAIa() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        var curriculo = new VinculoCurricular("História", "EF", 8, List.of("EF01XX99"));

        assertThatThrownBy(() -> sugestao.sugerir(new PedidoDeSugestaoDeCenario("Tema", curriculo)))
                .isInstanceOf(HabilidadesForaDoCatalogo.class)
                .hasMessageContaining("EF01XX99");
        verify(adaptador, never()).sugerirCenario(any());
    }

    @Test
    void falhaDoProvedorNaoGravaNada() {
        when(adaptador.sugerirCenario(any())).thenThrow(new FalhaNoProvedorDeIa("fora do ar", null));
        logar(PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> sugestao.sugerir(PEDIDO)).isInstanceOf(FalhaNoProvedorDeIa.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cenario WHERE autor_id = ?", Integer.class, professora))
                .isZero();
    }

    @Test
    void pedidoAusenteERecusado() {
        logar(PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> sugestao.sugerir(null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** RN-37: o administrador não usa conteúdo pedagógico. */
    @Test
    void administradorNaoPedeSugestao() {
        logar(PROFESSORA, "ROLE_ADMINISTRADOR");

        assertThatThrownBy(() -> sugestao.sugerir(PEDIDO)).isInstanceOf(AccessDeniedException.class);
        verify(adaptador, never()).sugerirCenario(any());
    }

    @Test
    void semLoginASugestaoERecusada() {
        assertThatThrownBy(() -> sugestao.sugerir(PEDIDO))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
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
