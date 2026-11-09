package br.ufg.inf.labsimulacoes.acesso.dominio;

import java.util.List;
import java.util.Optional;

/** Porta de persistência das turmas. A implementação fica na infraestrutura. */
public interface Turmas {

    /** Em ordem de escola, ano/série, turno e rótulo, como aparecem nas listas de escolha. */
    List<Turma> listar();

    Optional<Turma> buscar(long id);

    Turma salvar(Turma turma);

    /**
     * @throws TurmaEmUso se alguma sessão estiver ligada à turma, porque o histórico e as métricas
     *     por turma precisam dela (RN-31)
     */
    void excluir(long id);
}
