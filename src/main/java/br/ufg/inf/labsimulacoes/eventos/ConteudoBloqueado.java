package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * O filtro de conteúdo bloqueou uma pergunta ou uma frase da resposta (RN-20). Quando o evento sai, a
 * sessão já foi pausada; a Auditoria abre o caso na fila do curador (Stack, seção 4).
 *
 * <p>O texto vai copiado, como na sinalização (Stack, seção 6), para o caso não depender das
 * mensagens, que a retenção apaga. Uma pergunta bloqueada nem chega a virar mensagem.
 *
 * <p>No bloqueio da resposta, o evento sai na mesma transação da pausa, ainda sem os metadados, para
 * o caso nunca se perder depois de a sessão pausar. Quando o restante da resposta termina, sai de novo,
 * com os metadados; quem escuta reconhece a mesma ocorrência pela sessão e pelo momento.
 *
 * @param sessaoId sessão pausada pelo bloqueio
 * @param versaoId versão do cenário em uso na sessão
 * @param origem o que foi bloqueado
 * @param pergunta a pergunta da turma: a bloqueada, ou a que originou a resposta bloqueada
 * @param trechoBloqueado a frase da resposta que o filtro bloqueou; presente só quando a origem é a
 *     resposta, porque na pergunta o trecho é a própria pergunta
 * @param categorias categorias sinalizadas pelo provedor; pode vir vazia, porque o que decide o bloqueio
 *     é o texto ter sido sinalizado
 * @param respostaIaMetaId metadados da resposta bloqueada (RNF-10, RNF-13); ausente na pergunta e quando o
 *     provedor falhou antes de terminar a resposta
 * @param ocorridoEm momento do bloqueio
 */
public record ConteudoBloqueado(
        long sessaoId,
        long versaoId,
        Origem origem,
        String pergunta,
        String trechoBloqueado,
        List<String> categorias,
        Long respostaIaMetaId,
        Instant ocorridoEm) {

    public enum Origem {
        PERGUNTA,
        RESPOSTA
    }

    public ConteudoBloqueado {
        Objects.requireNonNull(origem, "origem");
        Objects.requireNonNull(pergunta, "pergunta");
        Objects.requireNonNull(ocorridoEm, "ocorridoEm");
        if ((origem == Origem.RESPOSTA) != (trechoBloqueado != null)) {
            throw new IllegalArgumentException("Só o bloqueio da resposta tem trecho, e ele sempre tem");
        }
        if (origem == Origem.PERGUNTA && respostaIaMetaId != null) {
            throw new IllegalArgumentException("A pergunta bloqueada não chega ao modelo, então não tem metadados");
        }
        categorias = categorias == null ? List.of() : List.copyOf(categorias);
    }

    /** A mesma ocorrência, agora com os metadados da resposta que terminou depois do bloqueio. */
    public ConteudoBloqueado comMetadados(long metadadosId) {
        return new ConteudoBloqueado(
                sessaoId, versaoId, origem, pergunta, trechoBloqueado, categorias, metadadosId, ocorridoEm);
    }
}
