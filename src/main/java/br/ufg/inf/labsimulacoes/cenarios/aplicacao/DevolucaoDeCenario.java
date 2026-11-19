package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntasDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.infraestrutura.EventosDeCenarios;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O curador devolve a versão em curadoria ao autor, com o que precisa mudar (RF-08, RN-07). A devolução
 * fecha a rodada de teste da versão (RF-09): as perguntas feitas até aqui deixam de contar para aprovar
 * (RN-06), porque a mesma versão volta a rascunho e o conteúdo pode mudar.
 */
@Service
@PreAuthorize("hasRole('CURADOR')")
public class DevolucaoDeCenario {

    private final Cenarios cenarios;
    private final RevisorLogado revisorLogado;
    private final EventosDeCenarios eventos;
    private final PerguntasDeTeste perguntasDeTeste;

    DevolucaoDeCenario(
            Cenarios cenarios,
            RevisorLogado revisorLogado,
            EventosDeCenarios eventos,
            PerguntasDeTeste perguntasDeTeste) {
        this.cenarios = cenarios;
        this.revisorLogado = revisorLogado;
        this.eventos = eventos;
        this.perguntasDeTeste = perguntasDeTeste;
    }

    /**
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.ComentarioObrigatorio se o comentário estiver vazio
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioForaDeCuradoria se a versão não estiver em
     *     curadoria
     */
    @Transactional
    public Cenario devolver(long id, String comentario) {
        Cenario cenario = cenarios.buscarParaAlterar(id).orElseThrow(() -> new CenarioNaoEncontrado(id));
        Cenario devolvido = cenarios.salvar(cenario.devolver(revisorLogado.atual(), comentario, RevisorLogado.agora()));
        perguntasDeTeste.fecharRodada(devolvido.versao().id(), devolvido.ultimaDevolucao().id());
        eventos.cenarioDevolvido(devolvido);
        return devolvido;
    }
}
