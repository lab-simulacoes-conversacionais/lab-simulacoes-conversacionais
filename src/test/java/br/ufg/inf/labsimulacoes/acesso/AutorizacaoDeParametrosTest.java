package br.ufg.inf.labsimulacoes.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.GestaoDeParametros;
import java.math.BigDecimal;
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

/**
 * Só o administrador altera os parâmetros (RN-37). O professor continua lendo os parâmetros pela
 * {@link ParametrosDeUso}, porque é ele quem inicia a sessão. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class AutorizacaoDeParametrosTest {

    @Autowired
    private GestaoDeParametros gestao;

    @Autowired
    private ParametrosDeUso parametros;

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administradorAlteraOsParametros() {
        comPerfis("ROLE_ADMINISTRADOR");

        gestao.alterar(30, new BigDecimal("5.00"));

        assertThat(gestao.consultar().limiteDePerguntasPorSessao()).isEqualTo(30);
    }

    @Test
    void professorECuradorNaoAlteramOsParametros() {
        comPerfis("ROLE_PROFESSOR", "ROLE_CURADOR");

        assertThatThrownBy(gestao::consultar).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> gestao.alterar(10, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void professorLeOsParametrosParaIniciarASessao() {
        comPerfis("ROLE_PROFESSOR");

        assertThat(parametros.limiteDePerguntasPorSessao()).isPositive();
    }

    @Test
    void semLoginAGestaoDeParametrosERecusada() {
        assertThatThrownBy(gestao::consultar).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private static void comPerfis(String... papeis) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("usuario@escola.br", null, papeis));
    }
}
