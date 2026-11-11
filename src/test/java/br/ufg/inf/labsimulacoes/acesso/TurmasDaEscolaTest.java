package br.ufg.inf.labsimulacoes.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Consulta das turmas pelos outros módulos, como o professor ao iniciar a sessão (RF-14, RN-32). */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class TurmasDaEscolaTest {

    @Autowired
    private TurmasDaEscola turmas;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    /** O cadastro é só do administrador, mas o professor precisa ler as turmas para escolher uma. */
    @Test
    void professorLeAsTurmasEmOrdem() {
        long oitavoB = inserir("8º ano", "8º B");
        long oitavoA = inserir("8º ano", "8º A");
        logar("ROLE_PROFESSOR");

        assertThat(turmas.listar()).extracting(TurmaDaEscola::id).containsSubsequence(oitavoA, oitavoB);
        assertThat(turmas.buscar(oitavoA))
                .contains(new TurmaDaEscola(oitavoA, "Escola Teste", "8º ano", "Manhã", "8º A"));
    }

    @Test
    void turmaInexistenteVemVazia() {
        logar("ROLE_PROFESSOR");

        assertThat(turmas.buscar(-1)).isEmpty();
    }

    @Test
    void semLoginERecusada() {
        assertThatThrownBy(() -> turmas.listar()).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private long inserir(String anoSerie, String rotulo) {
        return jdbc.queryForObject(
                "INSERT INTO turma (escola, ano_serie, turno, rotulo) VALUES ('Escola Teste', ?, 'Manhã', ?) RETURNING id",
                Long.class,
                anoSerie,
                rotulo);
    }

    private static void logar(String... perfis) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("qualquer@escola.test", null, perfis));
    }
}
