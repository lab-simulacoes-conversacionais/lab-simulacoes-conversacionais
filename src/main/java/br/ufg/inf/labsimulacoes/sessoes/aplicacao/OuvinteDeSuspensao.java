package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.eventos.CenarioSuspenso;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * RN-25: quando Cenários suspende o cenário, as sessões em andamento dele pausam, em qualquer versão. Novas
 * sessões já ficam bloqueadas, porque o cenário suspenso não tem versão que possa ir para a sala (RN-01).
 *
 * <p>Roda depois da transação de Cenários, em outra transação e sem usuário logado. Cada sessão pausa na
 * própria transação: se uma falhar, as outras pausam mesmo assim, e o ouvinte termina com erro no fim, para o
 * evento continuar pendente no registro de publicação e ser reprocessado quando a aplicação reiniciar. Na
 * reentrega, as sessões já pausadas ficam como estão. O log leva só ids.
 */
@Component
class OuvinteDeSuspensao {

    private static final Logger LOG = LoggerFactory.getLogger(OuvinteDeSuspensao.class);

    private final Sessoes sessoes;
    private final PausaPorSuspensao pausa;

    OuvinteDeSuspensao(Sessoes sessoes, PausaPorSuspensao pausa) {
        this.sessoes = sessoes;
        this.pausa = pausa;
    }

    /** @throws IllegalStateException se alguma sessão não pôde ser pausada; as outras já foram */
    @ApplicationModuleListener
    void cenarioSuspenso(CenarioSuspenso evento) {
        List<Long> falharam = new ArrayList<>();
        int pausadas = 0;
        for (long sessaoId : sessoes.emAndamentoDoCenario(evento.cenarioId())) {
            try {
                pausadas += pausa.pausar(sessaoId) ? 1 : 0;
            } catch (RuntimeException erro) {
                LOG.warn("Sessão {} não pausou com a suspensão do cenário {}", sessaoId, evento.cenarioId(), erro);
                falharam.add(sessaoId);
            }
        }
        LOG.info("Cenário {} suspenso: {} sessões em andamento pausadas", evento.cenarioId(), pausadas);
        if (!falharam.isEmpty()) {
            throw new IllegalStateException(
                    "As sessões " + falharam + " do cenário " + evento.cenarioId() + " não pausaram com a suspensão");
        }
    }
}
