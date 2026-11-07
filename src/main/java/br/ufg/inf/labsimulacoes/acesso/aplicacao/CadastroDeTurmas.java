package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.dominio.Turma;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaDuplicada;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaNaoEncontrada;
import br.ufg.inf.labsimulacoes.acesso.dominio.Turmas;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de turmas pelo administrador (RF-04, RN-37). */
@Service
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class CadastroDeTurmas {

    private final Turmas turmas;

    CadastroDeTurmas(Turmas turmas) {
        this.turmas = turmas;
    }

    @Transactional(readOnly = true)
    public List<Turma> listar() {
        return turmas.listar();
    }

    @Transactional(readOnly = true)
    public Turma buscar(long id) {
        return turmas.buscar(id).orElseThrow(() -> new TurmaNaoEncontrada(id));
    }

    @Transactional
    public Turma cadastrar(String escola, String anoSerie, String turno, String rotulo) {
        return salvarSemDuplicar(Turma.nova(escola, anoSerie, turno, rotulo));
    }

    @Transactional
    public Turma alterar(long id, String escola, String anoSerie, String turno, String rotulo) {
        return salvarSemDuplicar(buscar(id).alterar(escola, anoSerie, turno, rotulo));
    }

    /**
     * @throws br.ufg.inf.labsimulacoes.acesso.dominio.TurmaEmUso se a turma já tiver sessões
     */
    @Transactional
    public void excluir(long id) {
        buscar(id);
        turmas.excluir(id);
    }

    /**
     * A restrição uq_turma do banco também impede a duplicata, mas a checagem aqui devolve uma
     * mensagem que o formulário consegue mostrar. A lista é pequena, porque o piloto é numa escola só.
     */
    private Turma salvarSemDuplicar(Turma turma) {
        boolean duplicada = turmas.listar().stream()
                .anyMatch(outra -> !outra.id().equals(turma.id()) && outra.mesmaIdentificacaoQue(turma));
        if (duplicada) {
            throw new TurmaDuplicada(turma);
        }
        return turmas.salvar(turma);
    }
}
