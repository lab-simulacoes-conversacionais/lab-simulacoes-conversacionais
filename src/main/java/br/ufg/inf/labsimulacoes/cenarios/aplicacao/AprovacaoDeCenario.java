package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ChecklistsDeCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConferenciaDaCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ItemDoChecklist;
import br.ufg.inf.labsimulacoes.cenarios.dominio.MotivoDaReativacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntasDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.infraestrutura.EventosDeCenarios;
import java.util.Collection;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O curador aprova a versão em curadoria, que passa a ser a vigente (RF-08, RN-05, RN-08). A partir da
 * E2, só com o checklist de curadoria completo e o personagem testado com pelo menos 5 perguntas no modo
 * de teste (RF-10, RN-06). O administrador não decide sobre conteúdo pedagógico (RN-37).
 *
 * <p>Aprovar a versão nova de um cenário suspenso tira o cenário da suspensão (RN-23b): além do
 * {@code CenarioAprovado}, sai o {@code CenarioReativado}, na mesma transação, e o log de auditoria ganha as
 * duas linhas (RN-35). A trava do cenário é a mesma das regras de gravidade, então a aprovação e uma triagem
 * do mesmo cenário não se cruzam.
 */
@Service
@PreAuthorize("hasRole('CURADOR')")
public class AprovacaoDeCenario {

    private final Cenarios cenarios;
    private final PerguntasDeTeste perguntasDeTeste;
    private final ChecklistsDeCuradoria checklists;
    private final RevisorLogado revisorLogado;
    private final EventosDeCenarios eventos;
    private final ContagemDaSuspensao contagem;

    AprovacaoDeCenario(
            Cenarios cenarios,
            PerguntasDeTeste perguntasDeTeste,
            ChecklistsDeCuradoria checklists,
            RevisorLogado revisorLogado,
            EventosDeCenarios eventos,
            ContagemDaSuspensao contagem) {
        this.cenarios = cenarios;
        this.perguntasDeTeste = perguntasDeTeste;
        this.checklists = checklists;
        this.revisorLogado = revisorLogado;
        this.eventos = eventos;
        this.contagem = contagem;
    }

    /**
     * Conta as perguntas que o próprio curador fez no modo de teste, na rodada em aberto da versão: as de
     * outros curadores e as de antes de uma devolução não contam (RN-06). O checklist fica gravado junto
     * com a aprovação.
     *
     * @param marcados os itens do checklist que o curador marcou
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.AprovacaoIncompleta com tudo o que falta do
     *     checklist e do teste (RN-06)
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.DecisaoDeCuradoriaNaoPermitida se o curador for o
     *     autor (RN-36)
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioForaDeCuradoria se a versão não estiver em
     *     curadoria
     */
    @Transactional
    public Cenario aprovar(long id, Collection<ItemDoChecklist> marcados) {
        Cenario cenario = cenarios.buscarParaAlterar(id).orElseThrow(() -> new CenarioNaoEncontrado(id));
        Revisor curador = revisorLogado.atual();
        var conferencia = ConferenciaDaCuradoria.de(
                marcados, perguntasDeTeste.contar(cenario.versao().id(), curador.id()));
        Cenario aprovado = cenarios.salvar(cenario.aprovar(curador, conferencia, RevisorLogado.agora()));
        checklists.registrar(aprovado.versao().id(), curador.id(), conferencia);
        eventos.cenarioAprovado(aprovado);
        if (cenario.status() == StatusDoCenario.SUSPENSO) {
            eventos.cenarioReativado(aprovado, aprovado.versao().id(), curador.id(),
                    MotivoDaReativacao.NOVA_VERSAO_APROVADA, aprovado.versao().aprovacao().em());
            contagem.reativadoPelaAprovacao(aprovado.id(), aprovado.versao().id());
        }
        return aprovado;
    }

    /**
     * Quantas perguntas de teste o curador logado já fez na versão em curadoria e contam para o RN-06,
     * para a tela mostrar quanto falta antes de aprovar.
     */
    @Transactional(readOnly = true)
    public int perguntasDeTesteFeitas(long id) {
        Cenario cenario = cenarios.buscar(id).orElseThrow(() -> new CenarioNaoEncontrado(id));
        return perguntasDeTeste.contar(cenario.versao().id(), revisorLogado.atual().id());
    }
}
