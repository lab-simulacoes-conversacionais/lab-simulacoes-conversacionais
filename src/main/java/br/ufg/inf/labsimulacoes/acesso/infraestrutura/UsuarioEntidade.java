package br.ufg.inf.labsimulacoes.acesso.infraestrutura;

import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuario;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/** Mapeamento da tabela usuario. As anotações de JPA ficam aqui porque o domínio não usa framework. */
@Entity
@Table(name = "usuario")
class UsuarioEntidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativo;

    /** Sempre carregados, porque o domínio recebe o usuário fora da sessão do Hibernate. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_perfil", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "perfil", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Perfil> perfis = new HashSet<>();

    protected UsuarioEntidade() {}

    private UsuarioEntidade(Usuario usuario) {
        this.id = usuario.id();
        this.nome = usuario.nome();
        this.email = usuario.email();
        this.senhaHash = usuario.senhaHash();
        this.ativo = usuario.ativo();
        this.perfis = new HashSet<>(usuario.perfis());
    }

    static UsuarioEntidade de(Usuario usuario) {
        return new UsuarioEntidade(usuario);
    }

    Usuario paraDominio() {
        return new Usuario(id, nome, email, senhaHash, ativo, perfis);
    }
}
