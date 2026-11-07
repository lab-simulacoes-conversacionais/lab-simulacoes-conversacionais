package br.ufg.inf.labsimulacoes.acesso.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.dominio.Turma;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaEmUso;
import br.ufg.inf.labsimulacoes.acesso.dominio.Turmas;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Persistência das turmas no PostgreSQL real. Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class TurmasJpaTest {

    private static final String ESCOLA = "Escola de Teste das Turmas";

    @Autowired
    private Turmas turmas;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager jpa;

    /**
     * RN-32: uma coluna nova em turma precisa passar por esta lista, e nenhuma pode ser de estudante. O
     * filtro pelo esquema atual ignora as cópias que o MigrationsTest cria em outros esquemas.
     */
    @Test
    void tabelaTurmaTemSoAIdentificacaoEOsCamposDeControle() {
        var colunas = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'turma'""", String.class);

        assertThat(colunas).containsExactlyInAnyOrder(
                "id", "escola", "ano_serie", "turno", "rotulo", "criado_em", "atualizado_em");
    }

    @Test
    void salvarGeraIdentificadorEGuardaOsCampos() {
        Turma salva = turmas.salvar(Turma.nova(ESCOLA, "8º ano", "Matutino", "8º A"));

        assertThat(salva.id()).isNotNull();
        assertThat(turmas.buscar(salva.id())).contains(salva);
    }

    @Test
    void salvarTurmaExistenteAlteraOsCampos() {
        Turma salva = turmas.salvar(Turma.nova(ESCOLA, "8º ano", "Matutino", "8º A"));

        turmas.salvar(salva.alterar(ESCOLA, "8º ano", "Vespertino", "8º B"));

        assertThat(turmas.buscar(salva.id()))
                .contains(new Turma(salva.id(), ESCOLA, "8º ano", "Vespertino", "8º B"));
    }

    @Test
    void alterarAtualizaADataDeAlteracaoEMantemADeCriacao() {
        Turma salva = turmas.salvar(Turma.nova(ESCOLA, "8º ano", "Matutino", "8º A"));
        jdbc.update("UPDATE turma SET criado_em = '2026-01-01', atualizado_em = '2026-01-01' WHERE id = ?",
                salva.id());

        turmas.salvar(salva.alterar(ESCOLA, "8º ano", "Matutino", "8º B"));
        jpa.flush();

        var datas = jdbc.queryForMap(
                "SELECT criado_em = '2026-01-01' AS criacao_mantida, atualizado_em > '2026-01-01' AS atualizada"
                        + " FROM turma WHERE id = ?",
                salva.id());
        assertThat(datas).containsEntry("criacao_mantida", true).containsEntry("atualizada", true);
    }

    @Test
    void buscarTurmaInexistenteRetornaVazio() {
        assertThat(turmas.buscar(-1)).isEmpty();
    }

    @Test
    void listarOrdenaPorEscolaSerieTurnoERotulo() {
        Turma b = turmas.salvar(Turma.nova(ESCOLA, "8º ano", "Matutino", "8º B"));
        Turma noturno = turmas.salvar(Turma.nova(ESCOLA, "8º ano", "Noturno", "8º A"));
        Turma a = turmas.salvar(Turma.nova(ESCOLA, "8º ano", "Matutino", "8º A"));
        Turma nono = turmas.salvar(Turma.nova(ESCOLA, "9º ano", "Matutino", "9º A"));

        assertThat(turmas.listar().stream().filter(t -> t.escola().equals(ESCOLA)))
                .containsExactly(a, b, noturno, nono);
    }

    @Test
    void excluirTurmaSemSessoesApagaATurma() {
        Turma salva = turmas.salvar(Turma.nova(ESCOLA, "8º ano", "Matutino", "8º A"));

        turmas.excluir(salva.id());

        assertThat(turmas.buscar(salva.id())).isEmpty();
    }

    /** A falha da chave estrangeira aborta a transação, então o teste só confere a exceção. */
    @Test
    void excluirTurmaComSessaoERecusado() {
        Turma salva = turmas.salvar(Turma.nova(ESCOLA, "8º ano", "Matutino", "8º A"));
        inserirSessao(salva.id());

        assertThatThrownBy(() -> turmas.excluir(salva.id()))
                .isInstanceOf(TurmaEmUso.class)
                .hasMessageContaining(String.valueOf(salva.id()));
    }

    private void inserirSessao(long turma) {
        long usuario = id("""
                INSERT INTO usuario (nome, email, senha_hash)
                VALUES ('Professora', 'professora-turmas@escola.test', 'hash') RETURNING id""");
        long cenario = id("INSERT INTO cenario (autor_id) VALUES (?) RETURNING id", usuario);
        long versao = id("""
                INSERT INTO cenario_versao (cenario_id, numero, titulo, conteudo, criado_por_id)
                VALUES (?, 1, 'Título', '{}', ?) RETURNING id""", cenario, usuario);
        jdbc.update("INSERT INTO sessao (professor_id, turma_id, cenario_versao_id) VALUES (?, ?, ?)",
                usuario, turma, versao);
    }

    private long id(String sql, Object... argumentos) {
        return jdbc.queryForObject(sql, Long.class, argumentos);
    }
}
