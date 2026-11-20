package br.ufg.inf.labsimulacoes.metricas.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.ParametrosDeUso;
import br.ufg.inf.labsimulacoes.ia.ConsumoDaIa;
import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import br.ufg.inf.labsimulacoes.ia.ResumoDoConsumo;
import br.ufg.inf.labsimulacoes.metricas.dominio.AlertaDeCusto;
import br.ufg.inf.labsimulacoes.metricas.dominio.AlertasDeCusto;
import br.ufg.inf.labsimulacoes.metricas.dominio.DiaDeConsumo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * O que o administrador vê do consumo da IA (RNF-13, RN-37): o custo de hoje, o limite, o alerta do dia e
 * o custo de uma sessão pelo id. Só o administrador, que define o custo diário máximo. Nada é agrupado
 * por professor, para não virar ranking (RN-31).
 */
@Service
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ConsumoDaIaNaAdministracao {

    private final ConsumoDaIa consumo;
    private final ParametrosDeUso parametros;
    private final AlertasDeCusto alertas;
    private final ZoneId fuso;

    ConsumoDaIaNaAdministracao(
            ConsumoDaIa consumo,
            ParametrosDeUso parametros,
            AlertasDeCusto alertas,
            @Value("${metricas.custo.fuso}") ZoneId fuso) {
        this.consumo = consumo;
        this.parametros = parametros;
        this.alertas = alertas;
        this.fuso = fuso;
    }

    /**
     * O consumo de hoje até agora, no fuso da escola.
     *
     * @param limite o custo diário máximo atual; {@code null} se não foi definido
     * @param alerta o alerta de hoje, se houve; {@code null} se não houve
     */
    public record ConsumoDoDia(LocalDate dia, ResumoDoConsumo resumo, BigDecimal limite, AlertaDeCusto alerta) {

        /**
         * O custo de agora passou do limite de agora, mesmo que a conferência agendada ainda não tenha
         * rodado, pela mesma regra do alerta ({@link AlertaDeCusto#passouDoLimite}).
         */
        public boolean passouDoLimite() {
            return limite != null && AlertaDeCusto.passouDoLimite(resumo.custo(), limite);
        }

        /** O aviso do topo, com o custo já somado aqui, sem somar o dia de novo. Vazio sem alerta hoje. */
        public Optional<AvisoDeCusto> aviso() {
            return Optional.ofNullable(alerta).map(gravado -> new AvisoDeCusto(dia, resumo.custo(), gravado.limite()));
        }
    }

    /**
     * O aviso do topo das telas de administração enquanto houver alerta hoje, com o custo calculado na
     * hora, que pode já ter subido desde o alerta.
     *
     * @param custoAgora o custo de hoje até agora
     * @param limiteDoAlerta o limite que valia quando o alerta foi gravado
     */
    public record AvisoDeCusto(LocalDate dia, BigDecimal custoAgora, BigDecimal limiteDoAlerta) {}

    public ConsumoDoDia hoje() {
        DiaDeConsumo dia = DiaDeConsumo.de(Instant.now(), fuso);
        return new ConsumoDoDia(
                dia.dia(),
                consumo.custoEntre(dia.inicio(), dia.fim()),
                parametros.custoDiarioMaximoUsd().orElse(null),
                alertas.doDia(dia.dia()).orElse(null));
    }

    /**
     * O custo de uma sessão em sala: as respostas exibidas, as bloqueadas e as que vieram vazias. Uma
     * sessão que não existe, ou que ainda não teve resposta, vem zerada.
     */
    public ResumoDoConsumo daSessao(long sessaoId) {
        return consumo.daReferencia(FinalidadeDoConsumo.FALA_EM_SALA, sessaoId);
    }

    /**
     * Vazio enquanto hoje não houver alerta; o custo do dia só é somado quando há. Quem já leu {@link
     * #hoje()} usa {@link ConsumoDoDia#aviso()}, que não soma de novo.
     */
    public Optional<AvisoDeCusto> avisoDeHoje() {
        DiaDeConsumo dia = DiaDeConsumo.de(Instant.now(), fuso);
        return alertas.doDia(dia.dia()).map(alerta -> new AvisoDeCusto(
                dia.dia(), consumo.custoEntre(dia.inicio(), dia.fim()).custo(), alerta.limite()));
    }
}
