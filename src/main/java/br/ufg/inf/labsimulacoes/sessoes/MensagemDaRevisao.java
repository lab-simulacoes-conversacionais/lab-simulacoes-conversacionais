package br.ufg.inf.labsimulacoes.sessoes;

import java.time.Instant;
import java.util.Objects;

/**
 * Uma mensagem gravada, com o id, para a revisão da conversa apontar a resposta que vai ser sinalizada.
 *
 * @param daTurma pergunta da turma; senão, resposta da IA
 * @param respostaBloqueada só na pergunta da turma: a resposta a ela foi bloqueada e não virou mensagem
 */
public record MensagemDaRevisao(long id, boolean daTurma, String texto, Instant em, boolean respostaBloqueada) {

    public MensagemDaRevisao {
        Objects.requireNonNull(texto, "texto");
        Objects.requireNonNull(em, "em");
    }

    public boolean daIa() {
        return !daTurma;
    }
}
