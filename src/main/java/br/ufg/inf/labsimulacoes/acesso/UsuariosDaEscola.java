package br.ufg.inf.labsimulacoes.acesso;

import java.util.List;

/**
 * Consulta só de leitura dos nomes dos profissionais, para os outros módulos (Stack, seção 4). A Auditoria
 * a usa para mostrar quem fez cada ação no log e filtrar por usuário (RF-26); o cadastro continua só do
 * administrador.
 *
 * <p>Exige o perfil Curador ou Administrador, os que consultam o log.
 */
public interface UsuariosDaEscola {

    /** Todos, inclusive os desativados, em ordem de nome. */
    List<UsuarioDaEscola> listar();
}
