package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static br.ufg.inf.labsimulacoes.cenarios.TesteDoPersonagemFeito.CHECKLIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.BibliotecaDeCenarios;
import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import br.ufg.inf.labsimulacoes.cenarios.TesteDoPersonagemFeito;
import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CampoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ComparacaoDeVersoes;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.DiferencaDoCampo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNaoPermitida;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNoHistorico;
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

/**
 * Histórico, comparação e restauração com perfis e PostgreSQL reais (RF-11, RN-08, RN-37). As versões
 * passam pela curadoria de verdade. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class VersoesDoCenarioTest {

    private static final String PROFESSORA = "professora-versoes@escola.test";
    private static final String OUTRA_PROFESSORA = "outra-versoes@escola.test";
    private static final String CURADORA = "curadora-versoes@escola.test";
    private static final String ADMINISTRADORA = "admin-versoes@escola.test";
    private static final String TITULO_NOVO = "O voo do 14-bis, revisto";

    @Autowired
    private VersoesDoCenario versoes;

    @Autowired
    private AutoriaDeCenarios autoria;

    @Autowired
    private EnvioACuradoria envio;

    @Autowired
    private AprovacaoDeCenario aprovacao;

    @Autowired
    private DevolucaoDeCenario devolucao;

    @Autowired
    private AcervoDaCuradoria acervo;

    @Autowired
    private CenariosAprovados aprovados;

    @Autowired
    private BibliotecaDeCenarios biblioteca;

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

    // Editar um cenário aprovado (RN-08)

    @Test
    void autorEditaOAprovadoNumaVersaoNovaEAVigenteContinuaNaSala() {
        Cenario aprovado = aprovado();
        logar(PROFESSORA, "ROLE_PROFESSOR");

        Cenario comNova = versoes.criarNovaVersao(aprovado.id());
        autoria.alterarRascunho(aprovado.id(), comTitulo(TITULO_NOVO));
        envio.enviar(aprovado.id());

        assertThat(comNova.versao().numero()).isEqualTo(2);
        assertThat(comNova.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        logar(OUTRA_PROFESSORA, "ROLE_PROFESSOR");
        VersaoAprovada naSala = aprovados.versaoVigente(aprovado.id()).orElseThrow();
        assertThat(naSala.numero()).isEqualTo(1);
        assertThat(naSala.titulo()).isEqualTo("O voo do 14-bis");
        assertThat(biblioteca.listar(0, 20).cenarios()).singleElement()
                .satisfies(item -> assertThat(item.numeroDaVersao()).isEqualTo(1));
    }

    /** A versão nova vai para a fila do curador, e só a aprovação troca a vigente. */
    @Test
    void versaoNovaPassaPelaCuradoriaEAprovadaViraAVigente() {
        Cenario aprovado = aprovado();
        logar(PROFESSORA, "ROLE_PROFESSOR");
        versoes.criarNovaVersao(aprovado.id());
        autoria.alterarRascunho(aprovado.id(), comTitulo(TITULO_NOVO));
        envio.enviar(aprovado.id());

        logar(CURADORA, "ROLE_CURADOR");
        assertThat(acervo.listar(StatusDoCenario.EM_CURADORIA, 0, 20).itens()).extracting(Cenario::id)
                .containsExactly(aprovado.id());
        TesteDoPersonagemFeito.pelo(jdbc, CURADORA, aprovado.id());
        Cenario aprovadaANova = aprovacao.aprovar(aprovado.id(), CHECKLIST);

        assertThat(aprovadaANova.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(aprovados.versaoVigente(aprovado.id()).orElseThrow().titulo()).isEqualTo(TITULO_NOVO);
        assertThat(aprovados.versaoAprovada(aprovado.versao().id()).orElseThrow().titulo())
                .isEqualTo("O voo do 14-bis");
    }

    @Test
    void versaoNovaDevolvidaVoltaAoAutorSemTirarOCenarioDaSala() {
        Cenario aprovado = aprovado();
        logar(PROFESSORA, "ROLE_PROFESSOR");
        versoes.criarNovaVersao(aprovado.id());
        envio.enviar(aprovado.id());
        logar(CURADORA, "ROLE_CURADOR");

        Cenario devolvido = devolucao.devolver(aprovado.id(), "Nada mudou nesta versão.");

        assertThat(devolvido.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(devolvido.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(aprovados.versaoVigente(aprovado.id()).orElseThrow().numero()).isEqualTo(1);
        logar(PROFESSORA, "ROLE_PROFESSOR");
        assertThat(autoria.buscar(aprovado.id()).ultimaDevolucao().comentario()).isEqualTo("Nada mudou nesta versão.");
    }

    @Test
    void naoCriaVersaoNovaComOutraEmAndamentoNemDeCenarioSemVigente() {
        Cenario aprovado = aprovado();
        logar(PROFESSORA, "ROLE_PROFESSOR");
        versoes.criarNovaVersao(aprovado.id());
        long rascunho = autoria.criar(ConteudosDeTeste.completo()).id();

        assertThatThrownBy(() -> versoes.criarNovaVersao(aprovado.id())).isInstanceOf(VersaoNaoPermitida.class);
        assertThatThrownBy(() -> versoes.criarNovaVersao(rascunho)).isInstanceOf(VersaoNaoPermitida.class);
    }

    // Histórico e comparação

    @Test
    void historicoEComparacaoMostramAsVersoesEOQueMudou() {
        Cenario aprovado = aprovado();
        logar(PROFESSORA, "ROLE_PROFESSOR");
        versoes.criarNovaVersao(aprovado.id());
        autoria.alterarRascunho(aprovado.id(), comTitulo(TITULO_NOVO));

        List<VersaoNoHistorico> historico = versoes.historico(aprovado.id());
        ComparacaoDeVersoes comparacao = versoes.comparar(aprovado.id(), 2, 1);

        assertThat(historico).extracting(item -> item.versao().numero()).containsExactly(2, 1);
        assertThat(historico).extracting(VersaoNoHistorico::vigente).containsExactly(false, true);
        assertThat(comparacao.antiga().numero()).isEqualTo(1);
        assertThat(comparacao.diferencas()).extracting(DiferencaDoCampo::campo).containsExactly(CampoDoCenario.TITULO);
        assertThat(comparacao.diferencas().get(0).depois()).containsExactly(TITULO_NOVO);
    }

    /** RN-37: o curador vê o histórico de todos os cenários. */
    @Test
    void curadorVeOHistoricoDeQualquerCenario() {
        Cenario aprovado = aprovado();
        logar(CURADORA, "ROLE_CURADOR");

        assertThat(versoes.historico(aprovado.id())).hasSize(1);
        assertThat(versoes.comparar(aprovado.id(), 1, 1).iguais()).isTrue();
    }

    /** RN-37: o professor vê só os próprios cenários. */
    @Test
    void outroProfessorNaoVeOHistorico() {
        Cenario aprovado = aprovado();
        logar(OUTRA_PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> versoes.historico(aprovado.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> versoes.comparar(aprovado.id(), 1, 1)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void versaoOuCenarioInexistenteERecusado() {
        Cenario aprovado = aprovado();
        logar(PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> versoes.comparar(aprovado.id(), 1, 7)).isInstanceOf(VersaoNaoEncontrada.class);
        assertThatThrownBy(() -> versoes.restaurar(aprovado.id(), 7)).isInstanceOf(VersaoNaoEncontrada.class);
        assertThatThrownBy(() -> versoes.historico(-1)).isInstanceOf(CenarioNaoEncontrado.class);
        assertThatThrownBy(() -> versoes.criarNovaVersao(-1)).isInstanceOf(CenarioNaoEncontrado.class);
    }

    // Restauração

    @Test
    void restauraAVersaoAprovadaAnteriorComoNovoRascunho() {
        Cenario aprovado = aprovado();
        logar(PROFESSORA, "ROLE_PROFESSOR");
        versoes.criarNovaVersao(aprovado.id());
        autoria.alterarRascunho(aprovado.id(), comTitulo(TITULO_NOVO));
        envio.enviar(aprovado.id());
        logar(CURADORA, "ROLE_CURADOR");
        TesteDoPersonagemFeito.pelo(jdbc, CURADORA, aprovado.id());
        aprovacao.aprovar(aprovado.id(), CHECKLIST);
        logar(PROFESSORA, "ROLE_PROFESSOR");

        Cenario restaurado = versoes.restaurar(aprovado.id(), 1);

        assertThat(restaurado.versao().numero()).isEqualTo(3);
        assertThat(restaurado.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(versoes.comparar(aprovado.id(), 1, 3).iguais()).isTrue();
        assertThat(aprovados.versaoVigente(aprovado.id()).orElseThrow().titulo()).isEqualTo(TITULO_NOVO);
        assertThat(versoes.historico(aprovado.id())).extracting(item -> item.versao().status())
                .containsExactly(StatusDaVersao.RASCUNHO, StatusDaVersao.APROVADA, StatusDaVersao.APROVADA);
    }

    @Test
    void naoRestauraVersaoQueNaoFoiAprovada() {
        Cenario aprovado = aprovado();
        logar(PROFESSORA, "ROLE_PROFESSOR");
        versoes.criarNovaVersao(aprovado.id());

        assertThatThrownBy(() -> versoes.restaurar(aprovado.id(), 2))
                .isInstanceOf(VersaoNaoPermitida.class);
    }

    /** RN-08: as listas mostram a versão que vale e avisam da nova; a fila mostra a nova. */
    @Test
    void listasTrazemAUltimaAprovadaDeQuemTemVersaoNova() {
        Cenario aprovado = aprovado();
        logar(PROFESSORA, "ROLE_PROFESSOR");
        versoes.criarNovaVersao(aprovado.id());
        autoria.alterarRascunho(aprovado.id(), comTitulo(TITULO_NOVO));
        envio.enviar(aprovado.id());

        var meus = autoria.meusCenarios(0, 20);
        logar(CURADORA, "ROLE_CURADOR");
        var fila = acervo.listar(StatusDoCenario.EM_CURADORIA, 0, 20);

        Cenario meu = meus.itens().get(0);
        assertThat(meu.versao().conteudo().titulo()).isEqualTo(TITULO_NOVO);
        assertThat(meus.ultimaAprovada(meu)).hasValueSatisfying(versao -> {
            assertThat(versao.numero()).isEqualTo(1);
            assertThat(versao.conteudo().titulo()).isEqualTo("O voo do 14-bis");
        });
        assertThat(fila.ultimaAprovada(fila.itens().get(0))).isPresent();
    }

    /** Criar versões é do autor, como editar o rascunho; o curador ajusta pela devolução (RN-07). */
    @Test
    void soOAutorCriaOuRestauraVersoes() {
        Cenario aprovado = aprovado();

        logar(CURADORA, "ROLE_CURADOR");
        assertThatThrownBy(() -> versoes.criarNovaVersao(aprovado.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> versoes.restaurar(aprovado.id(), 1)).isInstanceOf(AccessDeniedException.class);
        logar(OUTRA_PROFESSORA, "ROLE_PROFESSOR");
        assertThatThrownBy(() -> versoes.criarNovaVersao(aprovado.id())).isInstanceOf(AccessDeniedException.class);
    }

    /**
     * RN-37: quem não é o autor recebe a mesma recusa para qualquer número de versão, e não descobre
     * quais versões existem.
     */
    @Test
    void quemNaoEAutorNaoDescobreAsVersoesDeOutroAutor() {
        Cenario aprovado = aprovado();
        logar(PROFESSORA, "ROLE_PROFESSOR");
        versoes.criarNovaVersao(aprovado.id());
        logar(OUTRA_PROFESSORA, "ROLE_PROFESSOR");

        for (int numero = 1; numero <= 3; numero++) {
            int pedida = numero;
            assertThatThrownBy(() -> versoes.restaurar(aprovado.id(), pedida))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void naoRestauraAVersaoQueJaVale() {
        Cenario aprovado = aprovado();
        logar(PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> versoes.restaurar(aprovado.id(), 1)).isInstanceOf(VersaoNaoPermitida.class);
    }

    /** Suspenso e arquivado não vão para a sala, então nenhuma versão deles aparece como vigente. */
    @Test
    void historicoDeSuspensoNaoMarcaVersaoVigente() {
        Cenario aprovado = aprovado();
        jdbc.update("UPDATE cenario SET status = 'SUSPENSO' WHERE id = ?", aprovado.id());
        logar(PROFESSORA, "ROLE_PROFESSOR");

        assertThat(versoes.historico(aprovado.id())).extracting(VersaoNoHistorico::vigente).containsExactly(false);
    }

    /** RN-37: o administrador não usa conteúdo pedagógico. */
    @Test
    void administradorESemLoginNaoUsamAsVersoes() {
        Cenario aprovado = aprovado();

        logar(ADMINISTRADORA, "ROLE_ADMINISTRADOR");
        assertThatThrownBy(() -> versoes.historico(aprovado.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> versoes.criarNovaVersao(aprovado.id())).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> versoes.historico(aprovado.id()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private Cenario aprovado() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        long id = autoria.criar(ConteudosDeTeste.completo()).id();
        envio.enviar(id);
        logar(CURADORA, "ROLE_CURADOR");
        TesteDoPersonagemFeito.pelo(jdbc, CURADORA, id);
        return aprovacao.aprovar(id, CHECKLIST);
    }

    private static ConteudoDoCenario comTitulo(String titulo) {
        var completo = ConteudosDeTeste.completo();
        return new ConteudoDoCenario(titulo, completo.roteiro(), completo.curriculo(), completo.faixaEtaria(),
                completo.duracaoPrevistaMin());
    }

    private static void logar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }
}
