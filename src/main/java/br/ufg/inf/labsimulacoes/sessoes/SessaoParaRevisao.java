package br.ufg.inf.labsimulacoes.sessoes;

import java.time.Instant;
import java.util.Objects;

/**
 * Uma sessão como a revisão da conversa mostra, com o título do cenário e o rótulo da turma já lidos.
 *
 * @param versaoId a versão com que a sessão começou (RN-08)
 * @param encerradaEm nulo se a sessão ainda não foi encerrada
 */
public record SessaoParaRevisao(
        long id,
        long professorId,
        long versaoId,
        String tituloDoCenario,
        String turma,
        Instant iniciadaEm,
        Instant encerradaEm) {

    public SessaoParaRevisao {
        Objects.requireNonNull(tituloDoCenario, "tituloDoCenario");
        Objects.requireNonNull(turma, "turma");
        Objects.requireNonNull(iniciadaEm, "iniciadaEm");
    }

    public boolean encerrada() {
        return encerradaEm != null;
    }
}
