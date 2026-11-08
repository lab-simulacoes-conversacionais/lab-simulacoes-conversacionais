package br.ufg.inf.labsimulacoes.acesso.infraestrutura;

import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UsuarioRepositorioJpa extends JpaRepository<UsuarioEntidade, Long> {

    /** Usa lower(), e não o IgnoreCase do Spring Data, para aproveitar o índice uq_usuario_email. */
    @Query("select u from UsuarioEntidade u where lower(u.email) = :email")
    Optional<UsuarioEntidade> buscarPorEmailNormalizado(@Param("email") String email);

    @Query("select count(u) from UsuarioEntidade u join u.perfis p where u.ativo = true and p = :perfil")
    long contarAtivosComPerfil(@Param("perfil") Perfil perfil);
}
