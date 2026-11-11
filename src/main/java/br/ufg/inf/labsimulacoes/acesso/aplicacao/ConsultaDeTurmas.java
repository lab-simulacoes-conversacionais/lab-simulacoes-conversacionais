package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.TurmaDaEscola;
import br.ufg.inf.labsimulacoes.acesso.TurmasDaEscola;
import br.ufg.inf.labsimulacoes.acesso.dominio.Turma;
import br.ufg.inf.labsimulacoes.acesso.dominio.Turmas;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura das turmas pelos outros módulos. Fica separada do {@link CadastroDeTurmas}, que é só do
 * administrador, porque quem lê é, por exemplo, o professor ao iniciar uma sessão.
 */
@Service
@PreAuthorize("isAuthenticated()")
class ConsultaDeTurmas implements TurmasDaEscola {

    private final Turmas turmas;

    ConsultaDeTurmas(Turmas turmas) {
        this.turmas = turmas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurmaDaEscola> listar() {
        return turmas.listar().stream().map(ConsultaDeTurmas::paraApi).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TurmaDaEscola> buscar(long id) {
        return turmas.buscar(id).map(ConsultaDeTurmas::paraApi);
    }

    private static TurmaDaEscola paraApi(Turma turma) {
        return new TurmaDaEscola(turma.id(), turma.escola(), turma.anoSerie(), turma.turno(), turma.rotulo());
    }
}
