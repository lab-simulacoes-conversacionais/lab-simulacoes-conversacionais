package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.dominio.Usuario;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuarios;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carrega quem pode entrar no sistema (RF-01).
 *
 * <p>Usuário inativo é tratado como inexistente: o Spring Security responde igual nos dois casos e
 * também à senha errada, inclusive no tempo de resposta, então a tela de login não revela quais
 * e-mails têm conta nem quais contas estão desativadas.
 *
 * <p>Cada perfil vira um papel do Spring Security, usado como {@code hasRole('PROFESSOR')} (RF-02).
 */
@Service
class AutenticacaoDeUsuarios implements UserDetailsService {

    private final Usuarios usuarios;

    AutenticacaoDeUsuarios(Usuarios usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return usuarios.buscarPorEmail(email)
                .filter(Usuario::podeEntrar)
                .map(AutenticacaoDeUsuarios::paraSpringSecurity)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado ou inativo"));
    }

    private static UserDetails paraSpringSecurity(Usuario usuario) {
        return User.withUsername(usuario.email())
                .password(usuario.senhaHash())
                .roles(usuario.perfis().stream().map(Enum::name).toArray(String[]::new))
                .build();
    }
}
