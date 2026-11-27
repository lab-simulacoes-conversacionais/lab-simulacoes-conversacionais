package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.MotivoDaReativacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.MotivoDaSuspensao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RegrasDeGravidade;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SinalizacaoDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SinalizacoesDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.infraestrutura.EventosDeCenarios;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Suspensão e reativação automáticas do cenário pelas regras de gravidade (RF-25, RN-23), a cada sinalização
 * ou triagem que chega da Auditoria por evento.
 *
 * <p>Em cada uma, nesta ordem: acha o cenário pela versão, trava o cenário até o fim da transação, guarda a
 * sinalização, refaz as regras com as sinalizações da versão vigente e só então muda o status. A trava vem
 * antes da gravação para que duas sinalizações do mesmo cenário ao mesmo tempo sejam decididas uma depois da
 * outra, e a segunda já veja a primeira. A aprovação de versão nova trava o mesmo cenário.
 *
 * <p>O evento {@code CenarioSuspenso} ou {@code CenarioReativado} só sai quando o status muda; por isso, a
 * mesma sinalização entregue de novo não gera outro evento nem outra linha no log (RN-35).
 *
 * <p>Não tem usuário logado nem confere perfil: quem chama é o ouvinte dos eventos, que roda depois da
 * transação da Auditoria. Eventos, log e métricas levam só ids e códigos (RNF-01).
 */
@Component
class SuspensaoAutomatica {

    static final String METRICA_DA_SUSPENSAO = "lab.cenarios.suspensao";
    static final String METRICA_DA_REATIVACAO = "lab.cenarios.reativacao";

    private static final Logger LOG = LoggerFactory.getLogger(SuspensaoAutomatica.class);

    private final Cenarios cenarios;
    private final SinalizacoesDoCenario sinalizacoes;
    private final EventosDeCenarios eventos;
    private final MeterRegistry metricas;

    SuspensaoAutomatica(
            Cenarios cenarios, SinalizacoesDoCenario sinalizacoes, EventosDeCenarios eventos, MeterRegistry metricas) {
        this.cenarios = cenarios;
        this.sinalizacoes = sinalizacoes;
        this.eventos = eventos;
        this.metricas = metricas;
    }

    /**
     * Uma sinalização nova. Só ela pode suspender pelo RN-23a, e quem fica como responsável é quem sinalizou.
     * Nunca reativa: uma sinalização a mais não tira motivo de suspensão.
     *
     * @param pendente com o momento da sinalização em {@code recebidaEm}
     * @param sinalizadoPorId quem sinalizou
     */
    @Transactional
    public void registrar(SinalizacaoDaVersao pendente, long sinalizadoPorId) {
        decidir(pendente, sinalizacoes::registrar, sinalizadoPorId, pendente.recebidaEm(), Optional.empty());
    }

    /**
     * A triagem do curador. Ela suspende pelo RN-23b ou RN-23c, ou reativa quando tira o último motivo: o
     * descarte (RN-23d) ou a confirmação da grave do professor com gravidade menor. Nos dois casos, o
     * responsável é o curador que triou.
     *
     * @param triada confirmada ou descartada; se o registro ainda não tiver chegado, {@code recebidaEm} é o
     *     momento da triagem
     */
    @Transactional
    public void triar(SinalizacaoDaVersao triada) {
        decidir(triada, sinalizacoes::triar, triada.triadaPorId(), triada.triadaEm(),
                Optional.of(MotivoDaReativacao.pelaTriagem(triada)));
    }

    private void decidir(
            SinalizacaoDaVersao sinalizacao,
            Predicate<SinalizacaoDaVersao> gravar,
            long responsavelId,
            Instant momento,
            Optional<MotivoDaReativacao> reativacao) {
        long versaoId = sinalizacao.versaoId();
        Optional<Long> doCenario = sinalizacoes.cenarioDaVersao(versaoId);
        if (doCenario.isEmpty()) {
            // Sem a versão não há onde guardar; o evento termina para não ficar pendente para sempre.
            ignorar(sinalizacao, null, "VERSAO_DESCONHECIDA");
            return;
        }
        long cenarioId = doCenario.get();
        Cenario cenario = cenarios.buscarParaAlterar(cenarioId).orElseThrow(() -> new CenarioNaoEncontrado(cenarioId));
        gravar.test(sinalizacao);

        if (!sinalizacoes.versaoVigente(cenarioId).map(vigente -> vigente == versaoId).orElse(false)) {
            ignorar(sinalizacao, cenarioId, "VERSAO_NAO_VIGENTE");
            return;
        }
        StatusDoCenario status = cenario.status();
        if (status != StatusDoCenario.APROVADO && status != StatusDoCenario.SUSPENSO) {
            ignorar(sinalizacao, cenarioId, "CENARIO_" + status.name());
            return;
        }

        Optional<MotivoDaSuspensao> motivo = RegrasDeGravidade.motivo(sinalizacoes.daVersao(versaoId));
        if (status == StatusDoCenario.APROVADO && motivo.isPresent()) {
            Cenario suspenso = cenarios.salvar(cenario.suspender());
            eventos.cenarioSuspenso(suspenso, versaoId, responsavelId, motivo.get(), momento);
            depoisDeConfirmar(() -> contar(
                    METRICA_DA_SUSPENSAO, "Cenários suspensos pelas regras de gravidade (RN-23)", motivo.get().name(),
                    "suspenso", cenarioId, versaoId, sinalizacao.sinalizacaoId()));
        } else if (status == StatusDoCenario.SUSPENSO && motivo.isEmpty() && reativacao.isPresent()) {
            Cenario reativado = cenarios.salvar(cenario.reativar());
            eventos.cenarioReativado(reativado, versaoId, responsavelId, reativacao.get(), momento);
            depoisDeConfirmar(() -> contar(
                    METRICA_DA_REATIVACAO, "Cenários suspensos que voltaram a Aprovado (RN-23)",
                    reativacao.get().name(), "reativado", cenarioId, versaoId, sinalizacao.sinalizacaoId()));
        }
    }

    private static void ignorar(SinalizacaoDaVersao sinalizacao, Long cenarioId, String motivo) {
        LOG.info("Sinalização {} ignorada na suspensão: cenário {}, versão {}, motivo {}",
                sinalizacao.sinalizacaoId(), cenarioId, sinalizacao.versaoId(), motivo);
    }

    private void contar(
            String metrica, String descricao, String motivo, String acao, long cenarioId, long versaoId,
            long sinalizacaoId) {
        Counter.builder(metrica)
                .description(descricao)
                .tag("motivo", motivo.toLowerCase(Locale.ROOT))
                .register(metricas)
                .increment();
        LOG.info("Cenário {} {}: versão {}, sinalização {}, motivo {}", cenarioId, acao, versaoId, sinalizacaoId, motivo);
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
