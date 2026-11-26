package br.ufg.inf.labsimulacoes.sessoes;

import java.time.Instant;
import java.util.Objects;

/**
 * Uma resposta da IA com a pergunta que a originou e os dados da sessão em que ela saiu, para a
 * sinalização de erro copiar (RF-23, Stack, seção 6).
 *
 * @param professorId o professor que conduziu a sessão
 * @param versaoId a versão do cenário em uso na sessão (RN-08)
 * @param sessaoEncerradaEm nulo se a sessão ainda não foi encerrada
 * @param pergunta a pergunta da turma logo antes da resposta
 * @param respostaIaMetaId os metadados da resposta (RNF-10); nulo numa resposta antiga gravada sem eles
 */
public record TrechoParaSinalizar(
        long sessaoId,
        long professorId,
        long versaoId,
        Instant sessaoIniciadaEm,
        Instant sessaoEncerradaEm,
        long mensagemId,
        String pergunta,
        String resposta,
        Instant respondidaEm,
        Long respostaIaMetaId) {

    public TrechoParaSinalizar {
        Objects.requireNonNull(sessaoIniciadaEm, "sessaoIniciadaEm");
        Objects.requireNonNull(pergunta, "pergunta");
        Objects.requireNonNull(resposta, "resposta");
        Objects.requireNonNull(respondidaEm, "respondidaEm");
    }
}
