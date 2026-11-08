package br.ufg.inf.labsimulacoes.acesso.infraestrutura;

import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuario;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuarios;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class UsuariosJpa implements Usuarios {

    private final UsuarioRepositorioJpa repositorio;

    UsuariosJpa(UsuarioRepositorioJpa repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        return repositorio.buscarPorEmailNormalizado(Usuario.normalizarEmail(email))
                .map(UsuarioEntidade::paraDominio);
    }

    @Override
    public Optional<Usuario> buscarPorId(long id) {
        return repositorio.findById(id).map(UsuarioEntidade::paraDominio);
    }

    @Override
    public List<Usuario> listar() {
        return repositorio.findAll(Sort.by("nome")).stream().map(UsuarioEntidade::paraDominio).toList();
    }

    @Override
    public long contarAdministradoresAtivos() {
        return repositorio.contarAtivosComPerfil(Perfil.ADMINISTRADOR);
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        return repositorio.save(UsuarioEntidade.de(usuario)).paraDominio();
    }
}
