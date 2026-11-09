package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuarios;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O nome do bean é usado nas expressões de {@code @PostAuthorize} dos outros módulos. */
@Service("usuarioLogado")
class ConsultaDoUsuarioLogado implements UsuarioLogado {

    private final Usuarios usuarios;

    ConsultaDoUsuarioLogado(Usuarios usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public long id() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarios.buscarPorEmail(email).orElseThrow().id();
    }
}
