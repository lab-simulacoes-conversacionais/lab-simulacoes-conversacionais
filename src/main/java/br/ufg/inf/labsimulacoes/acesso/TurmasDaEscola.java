package br.ufg.inf.labsimulacoes.acesso;

import java.util.List;
import java.util.Optional;

/**
 * Consulta só de leitura das turmas, para os outros módulos (Stack, seção 4). Sessões a usa para o
 * professor escolher a turma ao iniciar a sessão (RF-14); o cadastro continua só do administrador.
 *
 * <p>Exige um usuário autenticado.
 */
public interface TurmasDaEscola {

    /** Em ordem de escola, ano/série, turno e rótulo, como aparecem nas listas de escolha. */
    List<TurmaDaEscola> listar();

    /** Vazio se a turma não existir. */
    Optional<TurmaDaEscola> buscar(long id);
}
