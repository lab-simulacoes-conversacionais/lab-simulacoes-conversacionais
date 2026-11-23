package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MudancaNaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pausa automática aos 50 minutos de conversa (RF-19, RN-17).
 *
 * <p>A conferência roda quando a tela lê a sessão e antes de cada pergunta da turma. A tela de projeção
 * pede o painel de novo quando o cronômetro chega aos 50 minutos, então a pausa aparece na hora mesmo
 * sem ninguém clicar. Com a tela fechada, a pausa é feita na próxima leitura, mas registrada no momento
 * em que a conversa completou 50 minutos ({@link Sessao#pausarNoLimiteDeTempo}).
 *
 * <p>Não confere o professor: quem chama é o caso de uso, que já conferiu (RN-10). A pausa pelo tempo
 * vale para qualquer um que olhe a sessão.
 */
@Component
class LimiteDeTempo {

    private final Sessoes sessoes;
    private final EstadosDasSessoes estados;
    private final LeituraDaSessao leitura;
    private final Relogio relogio;

    LimiteDeTempo(Sessoes sessoes, EstadosDasSessoes estados, LeituraDaSessao leitura, Relogio relogio) {
        this.sessoes = sessoes;
        this.estados = estados;
        this.leitura = leitura;
        this.relogio = relogio;
    }

    /**
     * Pausa a sessão se ela chegou aos 50 minutos. Roda numa transação própria, para a pausa ficar
     * gravada mesmo quando quem chama só lê a sessão ou recusa a pergunta em seguida.
     *
     * <p>A primeira conferência é sem trava, porque quase sempre não há nada a fazer. A sessão só é
     * travada quando o tempo passou, e a regra é conferida de novo com ela travada, para dois pedidos ao
     * mesmo tempo não gravarem duas pausas.
     *
     * @return se a sessão foi pausada agora
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean conferir(long sessaoId) {
        Instant agora = relogio.agora();
        Sessao lida = sessoes.buscar(sessaoId).orElseThrow(() -> new SessaoNaoEncontrada(sessaoId));
        if (lida.status() != StatusDaSessao.EM_ANDAMENTO
                || lida.pausarNoLimiteDeTempo(leitura.doBanco(sessaoId), agora).isEmpty()) {
            return false;
        }
        Sessao sessao = sessoes.buscarParaAlterar(sessaoId).orElseThrow(() -> new SessaoNaoEncontrada(sessaoId));
        EstadoDaSessao antes = leitura.doBanco(sessaoId);
        Optional<MudancaNaSessao> pausa = sessao.pausarNoLimiteDeTempo(antes, agora);
        pausa.ifPresent(mudanca -> {
            sessoes.salvar(mudanca.sessao());
            sessoes.registrar(sessaoId, mudanca.evento());
            estados.gravar(antes.aplicar(mudanca.evento()));
        });
        return pausa.isPresent();
    }
}
