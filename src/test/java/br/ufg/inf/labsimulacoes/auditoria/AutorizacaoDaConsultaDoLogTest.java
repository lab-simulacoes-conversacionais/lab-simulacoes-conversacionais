package br.ufg.inf.labsimulacoes.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.ConsultaDoLog;
import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.FiltroDoLog;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * Só o curador e o administrador consultam o log (RF-26). Quem acumula o perfil de professor com o de
 * curador também consulta (RN-36). Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class AutorizacaoDaConsultaDoLogTest {

    @Autowired
    private ConsultaDoLog consulta;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void curadorConsultaOLog() {
        long usuario = acaoNoLog();
        comPerfis("ROLE_CURADOR");

        assertThat(consulta.consultar(doUsuario(usuario)))
                .extracting(RegistroDeAuditoria::acao)
                .containsExactly(AcaoCritica.ARQUIVAR_CENARIO);
    }

    @Test
    void administradorConsultaOLog() {
        long usuario = acaoNoLog();
        comPerfis("ROLE_ADMINISTRADOR");

        assertThat(consulta.consultar(doUsuario(usuario))).hasSize(1);
    }

    @Test
    void professorQueTambemECuradorConsultaOLog() {
        long usuario = acaoNoLog();
        comPerfis("ROLE_PROFESSOR", "ROLE_CURADOR");

        assertThat(consulta.consultar(doUsuario(usuario))).hasSize(1);
    }

    @Test
    void professorNaoConsultaOLog() {
        comPerfis("ROLE_PROFESSOR");

        assertThatThrownBy(() -> consulta.consultar(FiltroDoLog.tudo())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semLoginAConsultaERecusada() {
        assertThatThrownBy(() -> consulta.consultar(FiltroDoLog.tudo()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private long acaoNoLog() {
        long usuario = jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Curadora', ?, 'hash') RETURNING id",
                Long.class, UUID.randomUUID() + "@escola.test");
        jdbc.update("""
                INSERT INTO log_auditoria (usuario_id, acao, entidade, entidade_id, ocorrido_em)
                VALUES (?, 'ARQUIVAR_CENARIO', 'cenario', '9', ?)""",
                usuario, Timestamp.from(Instant.parse("2026-11-08T13:45:10Z")));
        return usuario;
    }

    private static FiltroDoLog doUsuario(long usuario) {
        return new FiltroDoLog(null, null, usuario, null, 0, FiltroDoLog.TAMANHO_MAXIMO);
    }

    private static void comPerfis(String... papeis) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("usuario@escola.br", null, papeis));
    }
}
