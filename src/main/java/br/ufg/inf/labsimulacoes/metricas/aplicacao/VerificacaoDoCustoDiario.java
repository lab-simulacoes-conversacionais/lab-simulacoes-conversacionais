package br.ufg.inf.labsimulacoes.metricas.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.ParametrosDeUso;
import br.ufg.inf.labsimulacoes.ia.ConsumoDaIa;
import br.ufg.inf.labsimulacoes.ia.ResumoDoConsumo;
import br.ufg.inf.labsimulacoes.metricas.dominio.AlertaDeCusto;
import br.ufg.inf.labsimulacoes.metricas.dominio.AlertasDeCusto;
import br.ufg.inf.labsimulacoes.metricas.dominio.DiaDeConsumo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Confere o custo da IA no dia e alerta o administrador quando ele passa do custo diário máximo
 * (RNF-13). Roda a cada {@code metricas.custo.verificacao}, sem login, então não é um caso de uso com
 * perfil: lê o limite e o consumo pelas APIs dos módulos, que não conferem perfil.
 *
 * <p>O dia é o do fuso {@code metricas.custo.fuso}, e o custo vem do banco, que é a fonte da verdade.
 * Inclui a fala em sala, o teste do curador e a sugestão de cenário; a moderação não tem custo. Há no
 * máximo um alerta por dia, garantido pelo banco: depois dele, o dia não é conferido de novo, nem se o
 * administrador mudar o limite. Sem limite definido, não há alerta.
 *
 * <p>O alerta fica gravado, sai como WARN no log e aparece no topo das telas de administração.
 */
@Component
class VerificacaoDoCustoDiario {

    private static final Logger LOG = LoggerFactory.getLogger(VerificacaoDoCustoDiario.class);

    private final ConsumoDaIa consumo;
    private final ParametrosDeUso parametros;
    private final AlertasDeCusto alertas;
    private final ZoneId fuso;

    VerificacaoDoCustoDiario(
            ConsumoDaIa consumo,
            ParametrosDeUso parametros,
            AlertasDeCusto alertas,
            @Value("${metricas.custo.fuso}") ZoneId fuso) {
        this.consumo = consumo;
        this.parametros = parametros;
        this.alertas = alertas;
        this.fuso = fuso;
    }

    /** A conferência agendada. Uma falha fica no log e a próxima conferência tenta de novo. */
    @Scheduled(fixedDelayString = "${metricas.custo.verificacao}", initialDelayString = "${metricas.custo.verificacao}")
    void verificarAgora() {
        try {
            verificar(Instant.now());
        } catch (RuntimeException e) {
            LOG.error("Não deu para conferir o custo diário da IA", e);
        }
    }

    /**
     * Confere o dia em que cai {@code agora}.
     *
     * @return o alerta gravado nesta conferência, ou vazio se não havia limite, se o custo não passou dele
     *     ou se o dia já estava alertado
     */
    Optional<AlertaDeCusto> verificar(Instant agora) {
        Optional<BigDecimal> limite = parametros.custoDiarioMaximoUsd();
        if (limite.isEmpty()) {
            return Optional.empty();
        }
        DiaDeConsumo dia = DiaDeConsumo.de(agora, fuso);
        if (alertas.doDia(dia.dia()).isPresent()) {
            return Optional.empty();
        }
        ResumoDoConsumo resumo = consumo.custoEntre(dia.inicio(), dia.fim());
        Optional<AlertaDeCusto> alerta = AlertaDeCusto.seUltrapassou(dia.dia(), resumo.custo(), limite);
        if (alerta.isEmpty() || !alertas.registrar(alerta.get())) {
            return Optional.empty();
        }
        LOG.warn("O custo da IA em {} passou do custo diário máximo: US$ {} de US$ {}{} (RNF-13)",
                dia.dia(), resumo.custo().toPlainString(), limite.get().toPlainString(),
                resumo.respostasSemCusto() == 0 ? "" : ", fora " + resumo.respostasSemCusto() + " respostas sem custo");
        return alerta;
    }
}
