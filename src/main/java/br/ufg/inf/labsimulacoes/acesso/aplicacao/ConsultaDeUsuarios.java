package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioDaEscola;
import br.ufg.inf.labsimulacoes.acesso.UsuariosDaEscola;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuarios;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura dos nomes dos profissionais pelos outros módulos. Fica separada da {@link GestaoDeUsuarios}, que é
 * só do administrador, porque o curador também consulta o log (RF-26).
 */
@Service
@PreAuthorize("hasAnyRole('CURADOR', 'ADMINISTRADOR')")
class ConsultaDeUsuarios implements UsuariosDaEscola {

    private final Usuarios usuarios;

    ConsultaDeUsuarios(Usuarios usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioDaEscola> listar() {
        return usuarios.listar().stream()
                .map(usuario -> new UsuarioDaEscola(usuario.id(), usuario.nome(), usuario.ativo()))
                .toList();
    }
}
