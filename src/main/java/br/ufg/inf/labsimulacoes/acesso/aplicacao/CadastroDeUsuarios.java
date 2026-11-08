package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuario;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuarios;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único caminho para criar usuários, para que a senha só chegue ao banco como hash (RNF-09).
 *
 * <p>Não verifica perfil nem publica eventos, porque não é um caso de uso: o administrador cadastra
 * pela {@link GestaoDeUsuarios}, e o {@link AdministradorInicial} cadastra antes de existir alguém
 * logado.
 */
@Service
public class CadastroDeUsuarios {

    private final Usuarios usuarios;
    private final PasswordEncoder codificador;

    CadastroDeUsuarios(Usuarios usuarios, PasswordEncoder codificador) {
        this.usuarios = usuarios;
        this.codificador = codificador;
    }

    @Transactional
    public Usuario cadastrar(String nome, String email, String senha, Set<Perfil> perfis) {
        if (senha == null || senha.isBlank()) {
            throw new IllegalArgumentException("O campo senha é obrigatório");
        }
        Usuario novo = Usuario.novo(nome, email, codificador.encode(senha), perfis);
        if (usuarios.buscarPorEmail(novo.email()).isPresent()) {
            throw new IllegalArgumentException("Já existe um usuário com este e-mail");
        }
        return usuarios.salvar(novo);
    }
}
