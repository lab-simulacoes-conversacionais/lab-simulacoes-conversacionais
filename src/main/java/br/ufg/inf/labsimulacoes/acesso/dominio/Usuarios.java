package br.ufg.inf.labsimulacoes.acesso.dominio;

import java.util.List;
import java.util.Optional;

/** Porta de persistência dos usuários. A implementação fica na infraestrutura. */
public interface Usuarios {

    /** Procura sem diferenciar maiúsculas de minúsculas. */
    Optional<Usuario> buscarPorEmail(String email);

    Optional<Usuario> buscarPorId(long id);

    /** Em ordem de nome. */
    List<Usuario> listar();

    long contarAdministradoresAtivos();

    Usuario salvar(Usuario usuario);
}
