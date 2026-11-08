package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuario;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuarios;
import br.ufg.inf.labsimulacoes.acesso.infraestrutura.PublicacaoDeEventos;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do administrador sobre os usuários (RF-03, RN-37).
 *
 * <p>O sistema nunca fica sem um administrador ativo, porque só outro administrador poderia corrigir
 * a situação.
 */
@Service
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class GestaoDeUsuarios {

    private final Usuarios usuarios;
    private final CadastroDeUsuarios cadastro;
    private final PublicacaoDeEventos eventos;

    GestaoDeUsuarios(Usuarios usuarios, CadastroDeUsuarios cadastro, PublicacaoDeEventos eventos) {
        this.usuarios = usuarios;
        this.cadastro = cadastro;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarios.listar();
    }

    @Transactional(readOnly = true)
    public Usuario buscar(long id) {
        return existente(id);
    }

    @Transactional
    public Usuario cadastrar(String nome, String email, String senha, Set<Perfil> perfis) {
        Usuario novo = cadastro.cadastrar(nome, email, senha, perfis);
        eventos.perfilAlterado(novo.id(), idDoUsuarioLogado(), Set.of(), novo.perfis());
        return novo;
    }

    @Transactional
    public Usuario atribuirPerfis(long id, Set<Perfil> perfis) {
        Usuario usuario = existente(id);
        Usuario alterado = usuario.comPerfis(perfis);
        if (alterado.perfis().equals(usuario.perfis())) {
            return usuario;
        }
        exigirOutroAdministrador(usuario, alterado);
        Usuario salvo = usuarios.salvar(alterado);
        eventos.perfilAlterado(id, idDoUsuarioLogado(), usuario.perfis(), salvo.perfis());
        return salvo;
    }

    @Transactional
    public Usuario ativar(long id) {
        return usuarios.salvar(existente(id).comAtivo(true));
    }

    /** O usuário perde o acesso na requisição seguinte, mesmo que já esteja logado. */
    @Transactional
    public Usuario desativar(long id) {
        Usuario usuario = existente(id);
        Usuario alterado = usuario.comAtivo(false);
        exigirOutroAdministrador(usuario, alterado);
        return usuarios.salvar(alterado);
    }

    private void exigirOutroAdministrador(Usuario antes, Usuario depois) {
        if (antes.ehAdministradorAtivo()
                && !depois.ehAdministradorAtivo()
                && usuarios.contarAdministradoresAtivos() <= 1) {
            throw new IllegalStateException("O sistema precisa ter ao menos um administrador ativo");
        }
    }

    private Usuario existente(long id) {
        return usuarios.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + id));
    }

    private long idDoUsuarioLogado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarios.buscarPorEmail(email).orElseThrow().id();
    }
}
