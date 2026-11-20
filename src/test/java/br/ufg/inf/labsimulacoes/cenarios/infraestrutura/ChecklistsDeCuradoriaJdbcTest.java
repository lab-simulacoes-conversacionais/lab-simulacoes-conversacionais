package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ChecklistsDeCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConferenciaDaCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ItemDoChecklist;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Checklist de curadoria no PostgreSQL real (RF-10, RN-06). Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class ChecklistsDeCuradoriaJdbcTest {

    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private ChecklistsDeCuradoria checklists;

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private Revisor curadora;
    private long versao;

    @BeforeEach
    void prepararVersaoAprovada() {
        long autora = inserirUsuario("autora-checklist-banco@escola.test");
        curadora = new Revisor(inserirUsuario("curadora-checklist-banco@escola.test"), true);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
        Cenario salvo = cenarios.salvar(Cenario.novo(autora, ConteudosDeTeste.completo()));
        Cenario enviado = cenarios.salvar(salvo.enviarACuradoria(autora));
        versao = cenarios.salvar(enviado.aprovar(curadora, ConteudosDeTeste.CONFERIDA, AGORA)).versao().id();
    }

    @Test
    void gravaELeAConferenciaDaAprovacao() {
        var conferencia = new ConferenciaDaCuradoria(Set.of(ItemDoChecklist.values()), 7);

        checklists.registrar(versao, curadora.id(), conferencia);

        assertThat(checklists.daVersao(versao)).contains(conferencia);
        assertThat(checklists.daVersao(-1)).isEmpty();
    }

    @Test
    void conferenciaIncompletaNaoEGravada() {
        var incompleta = new ConferenciaDaCuradoria(Set.of(ItemDoChecklist.FATOS_CONFERIDOS), 5);

        // O @Repository traduz a IllegalArgumentException para a exceção de acesso a dados do Spring.
        assertThatThrownBy(() -> checklists.registrar(versao, curadora.id(), incompleta))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThat(checklists.daVersao(versao)).isEmpty();
    }

    /** Uma versão é aprovada uma vez só, então tem um checklist só. */
    @Test
    void bancoRecusaOutroChecklistParaAMesmaVersao() {
        checklists.registrar(versao, curadora.id(), ConteudosDeTeste.CONFERIDA);

        assertThatThrownBy(() -> checklists.registrar(versao, curadora.id(), ConteudosDeTeste.CONFERIDA))
                .isInstanceOf(DataAccessException.class);
    }

    /** O banco repete a regra do RN-06: item desmarcado ou menos de 5 perguntas não entram. */
    @Test
    void bancoRecusaChecklistIncompletoGravadoPorFora() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO checklist_curadoria (cenario_versao_id, curador_id, fatos_conferidos, linguagem_adequada,
                                                 objetivos_coerentes, limites_respeitados, perguntas_de_teste)
                VALUES (?, ?, true, true, true, true, 4)""", versao, curadora.id()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void bancoRecusaAlterarOChecklist() {
        checklists.registrar(versao, curadora.id(), ConteudosDeTeste.CONFERIDA);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE checklist_curadoria SET perguntas_de_teste = 9 WHERE cenario_versao_id = ?", versao))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutável");
    }

    @Test
    void bancoRecusaApagarOChecklist() {
        checklists.registrar(versao, curadora.id(), ConteudosDeTeste.CONFERIDA);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM checklist_curadoria WHERE cenario_versao_id = ?", versao))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutável");
    }

    private long inserirUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
    }
}
