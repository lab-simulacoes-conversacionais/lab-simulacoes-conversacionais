package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static br.ufg.inf.labsimulacoes.cenarios.TesteDoPersonagemFeito.CHECKLIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.TesteDoPersonagemFeito;
import br.ufg.inf.labsimulacoes.cenarios.dominio.AprovacaoIncompleta;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ChecklistsDeCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConferenciaDaCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ItemDoChecklist;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.eventos.CenarioAprovado;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * Critério de aceite da #27: a aprovação fica bloqueada sem checklist completo e sem 5 perguntas de
 * teste (RF-10, RN-06), com perfis e PostgreSQL reais. As perguntas de teste são gravadas direto no
 * banco, sem chamar a IA. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
@RecordApplicationEvents
class AprovacaoComChecklistTest {

    private static final String PROFESSORA = "professora-checklist@escola.test";
    private static final String CURADORA = "curadora-checklist@escola.test";
    private static final String OUTRA_CURADORA = "outra-curadora-checklist@escola.test";

    @Autowired
    private AprovacaoDeCenario aprovacao;

    @Autowired
    private AutoriaDeCenarios autoria;

    @Autowired
    private EnvioACuradoria envio;

    @Autowired
    private DevolucaoDeCenario devolucao;

    @Autowired
    private ChecklistsDeCuradoria checklists;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents eventos;

    @BeforeEach
    void prepararUsuariosECatalogo() {
        for (String email : List.of(PROFESSORA, CURADORA, OUTRA_CURADORA)) {
            jdbc.update("INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash')", email);
        }
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aprovacaoGravaOChecklistEAsPerguntasDeTeste() {
        long id = enviado();
        TesteDoPersonagemFeito.pelo(jdbc, CURADORA, id);
        logar(CURADORA);

        Cenario aprovado = aprovacao.aprovar(id, CHECKLIST);

        assertThat(aprovado.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(checklists.daVersao(aprovado.versao().id()))
                .contains(new ConferenciaDaCuradoria(Set.of(ItemDoChecklist.values()), 5));
    }

    /** RN-06: sem as 5 perguntas de teste, nada muda e nada vai para o log. */
    @Test
    void semTesteNaoAprova() {
        long id = enviado();
        logar(CURADORA);

        assertThatThrownBy(() -> aprovacao.aprovar(id, CHECKLIST))
                .isInstanceOfSatisfying(AprovacaoIncompleta.class,
                        erro -> assertThat(erro.perguntasFaltando()).isEqualTo(5));
        assertThat(autoria.buscar(id).status()).isEqualTo(StatusDoCenario.EM_CURADORIA);
        assertThat(eventos.stream(CenarioAprovado.class)).isEmpty();
    }

    /** RN-06: cada item do checklist é obrigatório, mesmo com o teste feito. */
    @Test
    void checklistIncompletoNaoAprova() {
        long id = enviado();
        TesteDoPersonagemFeito.pelo(jdbc, CURADORA, id);
        logar(CURADORA);

        assertThatThrownBy(() -> aprovacao.aprovar(id, List.of(ItemDoChecklist.FATOS_CONFERIDOS)))
                .isInstanceOfSatisfying(AprovacaoIncompleta.class, erro -> assertThat(erro.itensFaltando())
                        .containsExactly(ItemDoChecklist.LINGUAGEM_ADEQUADA, ItemDoChecklist.OBJETIVOS_COERENTES,
                                ItemDoChecklist.LIMITES_RESPEITADOS));
        assertThatThrownBy(() -> aprovacao.aprovar(id, List.of())).isInstanceOf(AprovacaoIncompleta.class);
    }

    /** Quem aprova é quem testou: as perguntas de outra curadora não contam. */
    @Test
    void perguntasDeOutroCuradorNaoContam() {
        long id = enviado();
        TesteDoPersonagemFeito.pelo(jdbc, OUTRA_CURADORA, id);
        logar(CURADORA);

        assertThat(aprovacao.perguntasDeTesteFeitas(id)).isZero();
        assertThatThrownBy(() -> aprovacao.aprovar(id, CHECKLIST)).isInstanceOf(AprovacaoIncompleta.class);
    }

    /** A devolução fecha a rodada de teste: a versão reenviada precisa ser testada de novo. */
    @Test
    void perguntasDeAntesDaDevolucaoNaoContam() {
        long id = enviado();
        TesteDoPersonagemFeito.pelo(jdbc, CURADORA, id);
        logar(CURADORA);
        devolucao.devolver(id, "Corrigir a data do voo");
        logar(PROFESSORA);
        envio.enviar(id);
        logar(CURADORA);

        assertThat(aprovacao.perguntasDeTesteFeitas(id)).isZero();
        assertThatThrownBy(() -> aprovacao.aprovar(id, CHECKLIST)).isInstanceOf(AprovacaoIncompleta.class);
    }

    @Test
    void perguntasFeitasPeloCuradorLogado() {
        long id = enviado();
        TesteDoPersonagemFeito.pelo(jdbc, CURADORA, id);
        logar(CURADORA);

        assertThat(aprovacao.perguntasDeTesteFeitas(id)).isEqualTo(5);
    }

    private long enviado() {
        logar(PROFESSORA);
        long id = autoria.criar(ConteudosDeTeste.completo()).id();
        envio.enviar(id);
        return id;
    }

    private static void logar(String email) {
        String perfil = email.equals(PROFESSORA) ? "ROLE_PROFESSOR" : "ROLE_CURADOR";
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfil));
    }
}
