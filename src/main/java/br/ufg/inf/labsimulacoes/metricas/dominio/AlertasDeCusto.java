package br.ufg.inf.labsimulacoes.metricas.dominio;

import java.time.LocalDate;
import java.util.Optional;

/** Porta de persistência dos alertas de custo diário (RNF-13). A implementação fica na infraestrutura. */
public interface AlertasDeCusto {

    /**
     * Grava o alerta do dia, se ainda não houver um. Garantido pelo banco, então duas conferências ao
     * mesmo tempo não gravam dois alertas.
     *
     * @return {@code true} se este é o primeiro alerta do dia; {@code false} se o dia já estava alertado
     */
    boolean registrar(AlertaDeCusto alerta);

    Optional<AlertaDeCusto> doDia(LocalDate dia);
}
