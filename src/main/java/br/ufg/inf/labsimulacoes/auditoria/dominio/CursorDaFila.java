package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Posição da última sinalização de uma página da fila de triagem, de onde a página seguinte continua
 * (RF-24). Como a fila vai da mais antiga para a mais recente, a próxima página traz só as que vêm depois
 * desta; uma sinalização triada enquanto o curador lê a fila não faz as outras pularem de página.
 *
 * @param criadaEm quando foi criada a última sinalização da página
 * @param id a última sinalização da página, que desempata as criadas no mesmo momento
 */
public record CursorDaFila(Instant criadaEm, long id) {

    public CursorDaFila {
        Objects.requireNonNull(criadaEm, "criadaEm");
        if (id <= 0) {
            throw new IllegalArgumentException("O cursor precisa de uma sinalização gravada");
        }
    }

    /** Continua a fila depois desta sinalização. */
    public static CursorDaFila apos(SinalizacaoPendente sinalizacao) {
        return new CursorDaFila(sinalizacao.criadaEm(), sinalizacao.id());
    }
}
