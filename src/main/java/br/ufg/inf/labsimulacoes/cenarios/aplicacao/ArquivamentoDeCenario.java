package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.infraestrutura.EventosDeCenarios;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O curador arquiva um cenário, que sai da biblioteca sem perder o histórico (RF-13, RN-09). O
 * arquivamento vai para o log de auditoria pelo evento {@code CenarioArquivado} (RN-35). O professor e
 * o administrador não arquivam (RN-37).
 */
@Service
@PreAuthorize("hasRole('CURADOR')")
public class ArquivamentoDeCenario {

    private final Cenarios cenarios;
    private final RevisorLogado revisorLogado;
    private final EventosDeCenarios eventos;

    ArquivamentoDeCenario(Cenarios cenarios, RevisorLogado revisorLogado, EventosDeCenarios eventos) {
        this.cenarios = cenarios;
        this.revisorLogado = revisorLogado;
        this.eventos = eventos;
    }

    /**
     * @throws CenarioNaoEncontrado se o cenário não existir
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.ArquivamentoNaoPermitido se o cenário não estiver
     *     Aprovado nem Suspenso
     */
    @Transactional
    public Cenario arquivar(long id) {
        Cenario cenario = cenarios.buscarParaAlterar(id).orElseThrow(() -> new CenarioNaoEncontrado(id));
        Revisor curador = revisorLogado.atual();
        Instant momento = RevisorLogado.agora();
        Cenario arquivado = cenarios.salvar(cenario.arquivar(curador));
        eventos.cenarioArquivado(arquivado, curador.id(), momento);
        return arquivado;
    }
}
