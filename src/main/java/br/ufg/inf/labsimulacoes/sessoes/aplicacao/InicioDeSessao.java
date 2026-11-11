package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.TurmasDaEscola;
import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.CenarioSemVersaoAprovada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.TurmaComSessaoEmAndamento;
import br.ufg.inf.labsimulacoes.sessoes.dominio.TurmaNaoEncontrada;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O professor escolhe a turma e um cenário aprovado e inicia a sessão, ligada à versão vigente (RF-14).
 * Só o perfil Professor inicia sessões (RN-10).
 */
@Service
@PreAuthorize("hasRole('PROFESSOR')")
public class InicioDeSessao {

    private final Sessoes sessoes;
    private final EstadosDasSessoes estados;
    private final TurmasDaEscola turmas;
    private final CenariosAprovados cenarios;
    private final UsuarioLogado usuarioLogado;
    private final Relogio relogio;

    InicioDeSessao(
            Sessoes sessoes,
            EstadosDasSessoes estados,
            TurmasDaEscola turmas,
            CenariosAprovados cenarios,
            UsuarioLogado usuarioLogado,
            Relogio relogio) {
        this.sessoes = sessoes;
        this.estados = estados;
        this.turmas = turmas;
        this.cenarios = cenarios;
        this.usuarioLogado = usuarioLogado;
        this.relogio = relogio;
    }

    /**
     * A sessão guarda a versão vigente neste momento: se o cenário ganhar outra versão depois, esta
     * aula continua com a que começou (RN-08).
     *
     * @throws TurmaNaoEncontrada se a turma não existir
     * @throws CenarioSemVersaoAprovada se o cenário não existir ou não estiver aprovado (RN-01)
     * @throws TurmaComSessaoEmAndamento se a turma já tiver uma sessão em andamento (RN-10)
     */
    @Transactional
    public Sessao iniciar(long turmaId, long cenarioId) {
        if (turmas.buscar(turmaId).isEmpty()) {
            throw new TurmaNaoEncontrada(turmaId);
        }
        VersaoAprovada versao = cenarios.versaoVigente(cenarioId)
                .orElseThrow(() -> new CenarioSemVersaoAprovada(cenarioId));
        // O índice único do banco também recusa; a checagem aqui dá a mensagem sem depender do erro.
        if (sessoes.temSessaoEmAndamento(turmaId)) {
            throw new TurmaComSessaoEmAndamento(turmaId);
        }
        Sessao sessao = sessoes.salvar(
                Sessao.iniciar(usuarioLogado.id(), turmaId, versao.versaoId(), relogio.agora()));
        estados.gravar(EstadoDaSessao.inicial(sessao));
        return sessao;
    }
}
