package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MudancaNaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pausa uma sessão em andamento porque o cenário foi suspenso (RN-25), como os controles da sessão fazem: trava
 * a sessão, refaz o estado a partir do PostgreSQL, aplica antes a pausa dos 50 minutos se ela chegou (RN-17),
 * para o histórico ficar em ordem, e só então pausa com o motivo {@code CENARIO_SUSPENSO}. O Redis é gravado
 * depois da confirmação.
 *
 * <p>Cada sessão tem a própria transação, para a falha de uma não desfazer a pausa das outras do mesmo cenário.
 * Não confere o professor: quem chama é o ouvinte da suspensão, sem usuário logado.
 */
@Component
class PausaPorSuspensao {

    private final Sessoes sessoes;
    private final EstadosDasSessoes estados;
    private final LeituraDaSessao leitura;
    private final LimiteDeTempo limiteDeTempo;
    private final Relogio relogio;

    PausaPorSuspensao(
            Sessoes sessoes,
            EstadosDasSessoes estados,
            LeituraDaSessao leitura,
            LimiteDeTempo limiteDeTempo,
            Relogio relogio) {
        this.sessoes = sessoes;
        this.estados = estados;
        this.leitura = leitura;
        this.limiteDeTempo = limiteDeTempo;
        this.relogio = relogio;
    }

    /**
     * A sessão que já foi pausada ou encerrada desde a consulta fica como está; o mesmo vale para a suspensão
     * entregue de novo. Se a sessão chegou aos 50 minutos, ela fica pausada por esse motivo, que já foi gravado
     * antes, e não pausa uma segunda vez.
     *
     * @return se a sessão foi pausada agora pela suspensão
     * @throws SessaoNaoEncontrada se a sessão não existir
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean pausar(long sessaoId) {
        Sessao sessao = sessoes.buscarParaAlterar(sessaoId).orElseThrow(() -> new SessaoNaoEncontrada(sessaoId));
        EstadoDaSessao antes = leitura.doBanco(sessaoId);
        Optional<MudancaNaSessao> peloTempo = limiteDeTempo.pausarSeChegou(sessao, antes);
        if (peloTempo.isPresent()) {
            sessao = peloTempo.get().sessao();
            antes = antes.aplicar(peloTempo.get().evento());
        }
        Optional<MudancaNaSessao> pausa = sessao.suspenderCenario(relogio.agora());
        if (pausa.isEmpty()) {
            if (peloTempo.isPresent()) {
                estados.gravar(antes);
            }
            return false;
        }
        sessoes.salvar(pausa.get().sessao());
        sessoes.registrar(sessaoId, pausa.get().evento());
        estados.gravar(antes.aplicar(pausa.get().evento()));
        return true;
    }
}
