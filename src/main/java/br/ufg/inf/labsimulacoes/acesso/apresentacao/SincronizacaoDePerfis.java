package br.ufg.inf.labsimulacoes.acesso.apresentacao;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Faz a mudança do administrador valer na requisição seguinte (RF-03). O Spring Security guarda os
 * papéis na sessão ao fazer login, então sem este filtro um usuário desativado continuaria usando o
 * sistema, com os perfis antigos, até a sessão expirar.
 *
 * <p>Custa uma consulta ao banco por requisição autenticada. Os novos papéis valem só para a
 * requisição atual e são conferidos de novo na próxima, então não é preciso alterar a sessão.
 */
final class SincronizacaoDePerfis extends OncePerRequestFilter {

    private final UserDetailsService usuarios;

    SincronizacaoDePerfis(UserDetailsService usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao instanceof UsernamePasswordAuthenticationToken) {
            sincronizar(autenticacao, requisicao);
        }
        cadeia.doFilter(requisicao, resposta);
    }

    private void sincronizar(Authentication autenticacao, HttpServletRequest requisicao) {
        try {
            UserDetails usuario = usuarios.loadUserByUsername(autenticacao.getName());
            var atualizada = UsernamePasswordAuthenticationToken.authenticated(
                    usuario, null, usuario.getAuthorities());
            atualizada.eraseCredentials();
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(atualizada);
            SecurityContextHolder.setContext(contexto);
        } catch (UsernameNotFoundException inativo) {
            HttpSession sessao = requisicao.getSession(false);
            if (sessao != null) {
                sessao.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
    }
}
