package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioForaDeCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioIncompleto;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ComentarioObrigatorio;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.DecisaoDeCuradoriaNaoPermitida;
import br.ufg.inf.labsimulacoes.cenarios.dominio.EnvioNaoPermitido;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.eventos.CenarioAprovado;
import br.ufg.inf.labsimulacoes.eventos.CenarioDevolvido;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envio, aprovação e devolução com perfis e PostgreSQL reais (RF-08). Cada teste desfaz o que gravou.
 * Os eventos são conferidos no momento da publicação; o log de auditoria que os recebe depois do
 * commit é testado no módulo Auditoria.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
@RecordApplicationEvents
class CuradoriaDeCenariosTest {

    private static final String PROFESSORA = "professora-curadoria@escola.test";
    private static final String OUTRA_PROFESSORA = "outra-curadoria@escola.test";
    private static final String CURADORA = "curadora-curadoria@escola.test";
    private static final String PROFESSORA_E_CURADORA = "dois-perfis-curadoria@escola.test";
    private static final String ADMINISTRADORA = "admin-curadoria@escola.test";

    @Autowired
    private AutoriaDeCenarios autoria;

    @Autowired
    private EnvioACuradoria envio;

    @Autowired
    private AprovacaoDeCenario aprovacao;

    @Autowired
    private DevolucaoDeCenario devolucao;

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
        inserirUsuario(PROFESSORA_E_CURADORA);
        inserirUsuario(ADMINISTRADORA);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    // Envio

    @Test
    void professorEnviaOProprioCenario() {
        long id = criarComo(PROFESSORA, "ROLE_PROFESSOR");

        Cenario enviado = envio.enviar(id);

        assertThat(enviado.status()).isEqualTo(StatusDoCenario.EM_CURADORIA);
        assertThat(autoria.buscar(id).versao().status()).isEqualTo(StatusDaVersao.EM_CURADORIA);
    }

    /** RF-06: o curador também cria cenários, e o autor é quem envia. */
    @Test
    void curadorEnviaOCenarioQueCriou() {
        long id = criarComo(CURADORA, "ROLE_CURADOR");

        assertThat(envio.enviar(id).status()).isEqualTo(StatusDoCenario.EM_CURADORIA);
    }

    @Test
    void quemNaoEAutorNaoEnvia() {
        long id = criarComo(PROFESSORA, "ROLE_PROFESSOR");

        logar(CURADORA, "ROLE_CURADOR");
        assertThatThrownBy(() -> envio.enviar(id)).isInstanceOf(EnvioNaoPermitido.class);
        logar(OUTRA_PROFESSORA, "ROLE_PROFESSOR");
        assertThatThrownBy(() -> envio.enviar(id)).isInstanceOf(EnvioNaoPermitido.class);
    }

    @Test
    void cenarioIncompletoNaoEEnviado() {
        logar(PROFESSORA, "ROLE_PROFESSOR");
        long id = autoria.criar(ConteudosDeTeste.soComTitulo()).id();

        assertThatThrownBy(() -> envio.enviar(id)).isInstanceOf(CenarioIncompleto.class);
    }

    // Aprovação

    @Test
    void curadorAprovaEPublicaOEvento() {
        long id = enviadoPor(PROFESSORA);
        logar(CURADORA, "ROLE_CURADOR");

        Cenario aprovado = aprovacao.aprovar(id);

        assertThat(aprovado.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(aprovado.versao().status()).isEqualTo(StatusDaVersao.APROVADA);
        assertThat(aprovado.versao().aprovacao().curadorId()).isEqualTo(curadora);
        assertThat(eventos.stream(CenarioAprovado.class)).containsExactly(new CenarioAprovado(
                id, aprovado.versao().id(), curadora, aprovado.versao().aprovacao().em()));
    }

    /** RN-05: o professor não aprova, mesmo o cenário de outra pessoa. */
    @Test
    void professorNaoAprova() {
        long id = enviadoPor(PROFESSORA);
        logar(OUTRA_PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> aprovacao.aprovar(id)).isInstanceOf(AccessDeniedException.class);
        assertThat(eventos.stream(CenarioAprovado.class)).isEmpty();
    }

    /** RN-36: com os dois perfis, ela não aprova o cenário que criou como professora. */
    @Test
    void quemTemOsDoisPerfisNaoAprovaOQueCriou() {
        long id = enviadoPor(PROFESSORA_E_CURADORA);
        logar(PROFESSORA_E_CURADORA, "ROLE_PROFESSOR", "ROLE_CURADOR");

        assertThatThrownBy(() -> aprovacao.aprovar(id))
                .isInstanceOf(DecisaoDeCuradoriaNaoPermitida.class)
                .hasMessageContaining("RN-36");
        assertThat(eventos.stream(CenarioAprovado.class)).isEmpty();
    }

    /** RN-36 não impede outra curadora de aprovar o mesmo cenário. */
    @Test
    void outraCuradoraAprovaOCenarioDeQuemTemOsDoisPerfis() {
        long id = enviadoPor(PROFESSORA_E_CURADORA);
        logar(CURADORA, "ROLE_CURADOR");

        assertThat(aprovacao.aprovar(id).status()).isEqualTo(StatusDoCenario.APROVADO);
    }

    @Test
    void rascunhoNaoEAprovado() {
        long id = criarComo(PROFESSORA, "ROLE_PROFESSOR");
        logar(CURADORA, "ROLE_CURADOR");

        assertThatThrownBy(() -> aprovacao.aprovar(id)).isInstanceOf(CenarioForaDeCuradoria.class);
    }

    // Devolução

    @Test
    void curadorDevolveComComentarioEPublicaOEvento() {
        long id = enviadoPor(PROFESSORA);
        logar(CURADORA, "ROLE_CURADOR");

        Cenario devolvido = devolucao.devolver(id, "Conferir a data do voo com as fontes.");

        assertThat(devolvido.status()).isEqualTo(StatusDoCenario.RASCUNHO);
        assertThat(devolvido.ultimaDevolucao().comentario()).isEqualTo("Conferir a data do voo com as fontes.");
        assertThat(eventos.stream(CenarioDevolvido.class)).containsExactly(new CenarioDevolvido(
                id, devolvido.versao().id(), curadora, "Conferir a data do voo com as fontes.",
                devolvido.ultimaDevolucao().em()));
    }

    /** RN-07: depois da devolução, o autor vê o comentário e pode corrigir o rascunho. */
    @Test
    void autorVeOComentarioECorrigeORascunho() {
        long id = enviadoPor(PROFESSORA);
        logar(CURADORA, "ROLE_CURADOR");
        devolucao.devolver(id, "Conferir a data.");

        logar(PROFESSORA, "ROLE_PROFESSOR");
        Cenario lido = autoria.buscar(id);
        Cenario corrigido = autoria.alterarRascunho(id, ConteudosDeTeste.completo());

        assertThat(lido.ultimaDevolucao().comentario()).isEqualTo("Conferir a data.");
        assertThat(corrigido.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(envio.enviar(id).status()).isEqualTo(StatusDoCenario.EM_CURADORIA);
    }

    /** RN-07. */
    @Test
    void devolucaoSemComentarioERecusadaENadaMuda() {
        long id = enviadoPor(PROFESSORA);
        logar(CURADORA, "ROLE_CURADOR");

        assertThatThrownBy(() -> devolucao.devolver(id, "   ")).isInstanceOf(ComentarioObrigatorio.class);
        assertThatThrownBy(() -> devolucao.devolver(id, null)).isInstanceOf(ComentarioObrigatorio.class);
        assertThat(eventos.stream(CenarioDevolvido.class)).isEmpty();
        assertThat(autoria.buscar(id).status()).isEqualTo(StatusDoCenario.EM_CURADORIA);
    }

    @Test
    void professorNaoDevolve() {
        long id = enviadoPor(PROFESSORA);
        logar(OUTRA_PROFESSORA, "ROLE_PROFESSOR");

        assertThatThrownBy(() -> devolucao.devolver(id, "Corrigir")).isInstanceOf(AccessDeniedException.class);
    }

    // Versão aprovada vigente (RN-01)

    @Test
    void professorConsultaAVersaoAprovadaVigente() {
        long id = enviadoPor(PROFESSORA);
        logar(CURADORA, "ROLE_CURADOR");
        Cenario aprovado = aprovacao.aprovar(id);

        logar(OUTRA_PROFESSORA, "ROLE_PROFESSOR");
        VersaoAprovada vigente = aprovados.versaoVigente(id).orElseThrow();

        assertThat(vigente.cenarioId()).isEqualTo(id);
        assertThat(vigente.versaoId()).isEqualTo(aprovado.versao().id());
        assertThat(vigente.numero()).isEqualTo(1);
        assertThat(vigente.titulo()).isEqualTo("O voo do 14-bis");
        assertThat(vigente.personagem()).isEqualTo("Santos Dumont");
        assertThat(vigente.habilidades()).containsExactly("EF08HI03");
        assertThat(vigente.duracaoPrevistaMin()).isEqualTo(20);
        assertThat(vigente.aprovadaEm()).isEqualTo(aprovado.versao().aprovacao().em());
    }

    /** RN-01: rascunho, em curadoria e devolvido não vão para a sala. */
    @Test
    void cenarioNaoAprovadoNaoTemVersaoVigente() {
        long rascunho = criarComo(PROFESSORA, "ROLE_PROFESSOR");
        long emCuradoria = enviadoPor(PROFESSORA);
        long devolvido = enviadoPor(PROFESSORA);
        logar(CURADORA, "ROLE_CURADOR");
        devolucao.devolver(devolvido, "Corrigir");

        logar(PROFESSORA, "ROLE_PROFESSOR");
        assertThat(aprovados.versaoVigente(rascunho)).isEmpty();
        assertThat(aprovados.versaoVigente(emCuradoria)).isEmpty();
        assertThat(aprovados.versaoVigente(devolvido)).isEmpty();
        assertThat(aprovados.versaoVigente(-1)).isEmpty();
    }

    // Casos gerais de autorização

    @Test
    void cenarioInexistenteERecusado() {
        logar(CURADORA, "ROLE_CURADOR");

        assertThatThrownBy(() -> envio.enviar(-1)).isInstanceOf(CenarioNaoEncontrado.class);
        assertThatThrownBy(() -> aprovacao.aprovar(-1)).isInstanceOf(CenarioNaoEncontrado.class);
        assertThatThrownBy(() -> devolucao.devolver(-1, "Corrigir")).isInstanceOf(CenarioNaoEncontrado.class);
    }

    /** RN-37: o administrador não decide sobre conteúdo pedagógico. */
    @Test
    void administradorNaoParticipaDaCuradoria() {
        long id = enviadoPor(PROFESSORA);
        logar(ADMINISTRADORA, "ROLE_ADMINISTRADOR");

        assertThatThrownBy(() -> envio.enviar(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> aprovacao.aprovar(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> devolucao.devolver(id, "Corrigir")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> aprovados.versaoVigente(id)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semLoginACuradoriaERecusada() {
        long id = enviadoPor(PROFESSORA);
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> envio.enviar(id)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> aprovacao.aprovar(id)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> devolucao.devolver(id, "Corrigir"))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> aprovados.versaoVigente(id))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private long criarComo(String email, String... perfis) {
        logar(email, perfis);
        return autoria.criar(ConteudosDeTeste.completo()).id();
    }

    private long enviadoPor(String email) {
        long id = criarComo(email, "ROLE_PROFESSOR");
        envio.enviar(id);
        return id;
    }

    private long inserirUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
    }

    private static void logar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }
}
