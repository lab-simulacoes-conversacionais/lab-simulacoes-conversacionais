package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MudancaNaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pausa automática aos 50 minutos de conversa (RF-19, RN-17).
 *
 * <p>Toda mudança que trava a sessão confere o limite antes de mudar ({@link #pausarSeChegou}): os
 * controles do professor, a pergunta da turma e a leitura da tela quando o tempo passou. A tela de
 * projeção pede o painel de novo quando o cronômetro chega aos 50 minutos, então a pausa aparece na
 * hora mesmo sem ninguém clicar. Com a tela fechada, a pausa é feita na próxima leitura ou mudança, e
 * registrada no momento dos 50 minutos ({@link Sessao#pausarNoLimiteDeTempo}).
 *
 * <p>Não confere o professor: quem chama é o caso de uso, que já conferiu (RN-10).
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
     * Para a leitura da tela, depois que o estado do Redis mostrou que o tempo passou: trava a sessão,
     * confere de novo com o estado do banco e pausa, se for o caso. A conferência com a trava evita que
     * dois pedidos ao mesmo tempo gravem duas pausas.
     *
     * @return se a sessão foi pausada agora
     */
    @Transactional
    public boolean conferir(long sessaoId) {
        Sessao sessao = sessoes.buscarParaAlterar(sessaoId).orElseThrow(() -> new SessaoNaoEncontrada(sessaoId));
        EstadoDaSessao antes = leitura.doBanco(sessaoId);
        Optional<MudancaNaSessao> pausa = pausarSeChegou(sessao, antes);
        pausa.ifPresent(mudanca -> estados.gravar(antes.aplicar(mudanca.evento())));
        return pausa.isPresent();
    }

    /**
     * Pausa a sessão se ela chegou aos 50 minutos, gravando a sessão e o evento. Quem chama já travou a
     * sessão, está na transação e grava o estado no Redis com o que fizer em seguida.
     *
     * @param travada a sessão lida com a trava
     * @param antes o estado refeito do banco com a sessão travada
     * @return a pausa gravada, ou vazio se ainda não era hora
     */
    Optional<MudancaNaSessao> pausarSeChegou(Sessao travada, EstadoDaSessao antes) {
        Instant agora = relogio.agora();
        Optional<MudancaNaSessao> pausa = travada.pausarNoLimiteDeTempo(antes, agora);
        pausa.ifPresent(mudanca -> {
            sessoes.salvar(mudanca.sessao());
            sessoes.registrar(travada.id(), mudanca.evento());
        });
        return pausa;
    }
}
