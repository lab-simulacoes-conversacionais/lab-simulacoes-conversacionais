package br.ufg.inf.labsimulacoes.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeTurmas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Só o administrador gerencia as turmas (RN-37). Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class AutorizacaoDeTurmasTest {

    @Autowired
    private CadastroDeTurmas turmas;

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administradorGerenciaTurmas() {
        comPerfis("ROLE_ADMINISTRADOR");

        var turma = turmas.cadastrar("Escola Teste RN-37", "8º ano", "Manhã", "A");

        assertThat(turmas.buscar(turma.id())).isEqualTo(turma);
    }

    @Test
    void professorECuradorNaoGerenciamTurmas() {
        comPerfis("ROLE_PROFESSOR", "ROLE_CURADOR");

        assertThatThrownBy(turmas::listar).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> turmas.buscar(1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> turmas.cadastrar("Escola", "8º ano", "Manhã", "A"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> turmas.alterar(1, "Escola", "8º ano", "Manhã", "A"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> turmas.excluir(1)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semLoginAsTurmasSaoRecusadas() {
        assertThatThrownBy(turmas::listar).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private static void comPerfis(String... papeis) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("usuario@escola.br", null, papeis));
    }
}
