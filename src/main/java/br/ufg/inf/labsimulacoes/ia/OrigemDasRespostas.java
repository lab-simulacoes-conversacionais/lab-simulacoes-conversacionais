package br.ufg.inf.labsimulacoes.ia;

import java.util.Optional;

/**
 * A origem de uma resposta já gravada, pelo id dos metadados que quem a guardou recebeu em {@link
 * RespostaDoPersonagem#metadadosId()}. Serve para copiar a origem antes que a retenção apague os
 * metadados (RN-33), como faz a sinalização de erro (RF-23).
 */
public interface OrigemDasRespostas {

    /** @return vazio se não houver metadados com esse id */
    Optional<OrigemDaResposta> daResposta(long respostaIaMetaId);
}
