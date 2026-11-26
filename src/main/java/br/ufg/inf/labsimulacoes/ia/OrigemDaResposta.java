package br.ufg.inf.labsimulacoes.ia;

import java.util.Objects;

/**
 * De onde veio uma resposta da IA: o provedor, o modelo e a versão do prompt (RNF-10). É a parte dos
 * metadados que outro módulo pode copiar, como a sinalização de erro (RF-23); tokens, custo e parâmetros
 * ficam no adaptador.
 */
public record OrigemDaResposta(String provedor, String modelo, String versaoPrompt) {

    public OrigemDaResposta {
        Objects.requireNonNull(provedor, "provedor");
        Objects.requireNonNull(modelo, "modelo");
        Objects.requireNonNull(versaoPrompt, "versaoPrompt");
    }
}
