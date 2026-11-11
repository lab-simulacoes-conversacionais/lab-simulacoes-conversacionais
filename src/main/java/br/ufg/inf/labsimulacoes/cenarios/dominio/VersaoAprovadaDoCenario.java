package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Objects;

/**
 * Uma versão aprovada com o cenário a que pertence. Pode já não ser a vigente: a sessão continua com
 * a versão com que começou (RN-08).
 *
 * @param versao imutável depois de aprovada (RN-08)
 */
public record VersaoAprovadaDoCenario(long cenarioId, VersaoDoCenario versao) {

    public VersaoAprovadaDoCenario {
        Objects.requireNonNull(versao, "versao");
    }
}
