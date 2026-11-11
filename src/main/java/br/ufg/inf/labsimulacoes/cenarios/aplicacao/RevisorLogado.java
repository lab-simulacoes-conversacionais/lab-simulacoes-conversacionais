package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Quem está decidindo sobre o cenário, com o perfil que a requisição tem. Os perfis vêm do Spring
 * Security, que os confere no banco a cada requisição (README, Perfis e autorização).
 */
@Component
class RevisorLogado {

    private static final String PERFIL_CURADOR = "ROLE_CURADOR";

    private final UsuarioLogado usuarioLogado;

    RevisorLogado(UsuarioLogado usuarioLogado) {
        this.usuarioLogado = usuarioLogado;
    }

    Revisor atual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        boolean curador = autenticacao.getAuthorities().stream()
                .anyMatch(perfil -> PERFIL_CURADOR.equals(perfil.getAuthority()));
        return new Revisor(usuarioLogado.id(), curador);
    }

    /** O PostgreSQL guarda microssegundos; assim o momento lido do banco é igual ao do evento. */
    static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
