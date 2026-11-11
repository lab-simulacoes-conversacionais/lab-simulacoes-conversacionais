package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.infraestrutura.EventosDeCenarios;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O curador aprova a versão em curadoria, que passa a ser a vigente (RF-08, RN-05, RN-08). Na E1 a
 * aprovação é só registrada, sem checklist (RN-06). O administrador não decide sobre conteúdo
 * pedagógico (RN-37).
 */
@Service
@PreAuthorize("hasRole('CURADOR')")
public class AprovacaoDeCenario {

    private final Cenarios cenarios;
    private final RevisorLogado revisorLogado;
    private final EventosDeCenarios eventos;

    AprovacaoDeCenario(Cenarios cenarios, RevisorLogado revisorLogado, EventosDeCenarios eventos) {
        this.cenarios = cenarios;
        this.revisorLogado = revisorLogado;
        this.eventos = eventos;
    }

    /**
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.DecisaoDeCuradoriaNaoPermitida se o curador for o
     *     autor (RN-36)
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioForaDeCuradoria se a versão não estiver em
     *     curadoria
     */
    @Transactional
    public Cenario aprovar(long id) {
        Cenario cenario = cenarios.buscarParaAlterar(id).orElseThrow(() -> new CenarioNaoEncontrado(id));
        Cenario aprovado = cenarios.salvar(cenario.aprovar(revisorLogado.atual(), RevisorLogado.agora()));
        eventos.cenarioAprovado(aprovado);
        return aprovado;
    }
}
