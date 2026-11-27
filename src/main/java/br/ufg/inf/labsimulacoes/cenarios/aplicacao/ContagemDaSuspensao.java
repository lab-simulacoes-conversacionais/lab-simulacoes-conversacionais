package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.MotivoDaReativacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.MotivoDaSuspensao;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Métricas e log INFO da suspensão e da reativação do cenário (RF-25), contados só depois da confirmação da
 * transação, para uma mudança desfeita não aparecer. Só ids e códigos (RNF-01).
 */
@Component
class ContagemDaSuspensao {

    static final String METRICA_DA_SUSPENSAO = "lab.cenarios.suspensao";
    static final String METRICA_DA_REATIVACAO = "lab.cenarios.reativacao";

    private static final Logger LOG = LoggerFactory.getLogger(ContagemDaSuspensao.class);

    private final MeterRegistry metricas;

    ContagemDaSuspensao(MeterRegistry metricas) {
        this.metricas = metricas;
    }

    /** @param sinalizacaoId a sinalização ou a triagem que suspendeu o cenário */
    void suspenso(long cenarioId, long versaoId, long sinalizacaoId, MotivoDaSuspensao motivo) {
        depoisDeConfirmar(() -> {
            contar(METRICA_DA_SUSPENSAO, "Cenários suspensos pelas regras de gravidade (RN-23)", motivo.name());
            LOG.info("Cenário {} suspenso: versão {}, sinalização {}, motivo {}",
                    cenarioId, versaoId, sinalizacaoId, motivo);
        });
    }

    /** @param sinalizacaoId a triagem que reativou o cenário */
    void reativado(long cenarioId, long versaoId, long sinalizacaoId, MotivoDaReativacao motivo) {
        depoisDeConfirmar(() -> {
            contarReativacao(motivo);
            LOG.info("Cenário {} reativado: versão {}, sinalização {}, motivo {}",
                    cenarioId, versaoId, sinalizacaoId, motivo);
        });
    }

    /** RN-23b: a aprovação da versão nova, que não vem de uma sinalização. */
    void reativadoPelaAprovacao(long cenarioId, long versaoId) {
        depoisDeConfirmar(() -> {
            contarReativacao(MotivoDaReativacao.NOVA_VERSAO_APROVADA);
            LOG.info("Cenário {} reativado: versão {}, motivo {}",
                    cenarioId, versaoId, MotivoDaReativacao.NOVA_VERSAO_APROVADA);
        });
    }

    private void contarReativacao(MotivoDaReativacao motivo) {
        contar(METRICA_DA_REATIVACAO, "Cenários suspensos que voltaram a Aprovado (RN-23)", motivo.name());
    }

    private void contar(String metrica, String descricao, String motivo) {
        Counter.builder(metrica)
                .description(descricao)
                .tag("motivo", motivo.toLowerCase(Locale.ROOT))
                .register(metricas)
                .increment();
    }

    /** Fora de uma transação, a gravação já está confirmada. */
    private static void depoisDeConfirmar(Runnable acao) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            acao.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                acao.run();
            }
        });
    }
}
