package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Uma versão no histórico do cenário (RF-11).
 *
 * @param criadaEm quando a versão foi criada
 * @param vigente se é a versão que vale em sala agora: a última aprovada, e só com o cenário Aprovado.
 *     Suspenso e arquivado não vão para a sala, então nenhuma versão deles é vigente (RN-01, RN-08)
 */
public record VersaoNoHistorico(VersaoDoCenario versao, Instant criadaEm, boolean vigente) {

    public VersaoNoHistorico {
        Objects.requireNonNull(versao, "versao");
        Objects.requireNonNull(criadaEm, "criadaEm");
    }
}
