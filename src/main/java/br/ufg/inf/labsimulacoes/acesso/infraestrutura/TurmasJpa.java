package br.ufg.inf.labsimulacoes.acesso.infraestrutura;

import br.ufg.inf.labsimulacoes.acesso.dominio.Turma;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaEmUso;
import br.ufg.inf.labsimulacoes.acesso.dominio.Turmas;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class TurmasJpa implements Turmas {

    private final TurmaRepositorioJpa repositorio;

    TurmasJpa(TurmaRepositorioJpa repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public List<Turma> listar() {
        return repositorio.findAllByOrderByEscolaAscAnoSerieAscTurnoAscRotuloAsc().stream()
                .map(TurmaEntidade::paraDominio)
                .toList();
    }

    @Override
    public Optional<Turma> buscar(long id) {
        return repositorio.findById(id).map(TurmaEntidade::paraDominio);
    }

    @Override
    public Turma salvar(Turma turma) {
        return repositorio.save(TurmaEntidade.de(turma)).paraDominio();
    }

    /**
     * Quem guarda as sessões é o módulo Sessões, que depende deste, e não o contrário. Por isso a
     * turma não consulta as sessões: a chave estrangeira de sessao.turma_id recusa a exclusão, e o
     * flush faz isso acontecer aqui, e não só no fim da transação.
     */
    @Override
    @Transactional
    public void excluir(long id) {
        try {
            repositorio.deleteById(id);
            repositorio.flush();
        } catch (DataIntegrityViolationException e) {
            throw new TurmaEmUso(id);
        }
    }
}
