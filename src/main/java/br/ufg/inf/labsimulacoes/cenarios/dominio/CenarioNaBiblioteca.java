package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Objects;

/**
 * Cenário Aprovado, com a versão vigente que vai para a sala (RF-12, RN-01).
 *
 * @param versao a versão aprovada vigente, que não muda mais (RN-08)
 */
public record CenarioNaBiblioteca(long cenarioId, VersaoDoCenario versao) {

    public CenarioNaBiblioteca {
        Objects.requireNonNull(versao, "versao");
    }
}
