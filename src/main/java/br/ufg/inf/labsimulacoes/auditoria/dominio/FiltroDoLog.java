package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;

/**
 * Filtros da consulta do log de ações críticas (RF-26). Todos são opcionais e se combinam. O
 * resultado vem do mais recente para o mais antigo, em páginas.
 *
 * @param inicio ações que ocorreram a partir deste instante, inclusive
 * @param fim ações que ocorreram antes deste instante, que fica de fora; assim, dois períodos
 *     seguidos não repetem a ação que caiu bem na divisa
 * @param usuarioId ações feitas por este usuário
 * @param acao ações deste tipo
 * @param pagina número da página, a partir de zero
 * @param tamanho número máximo de linhas na página
 */
public record FiltroDoLog(Instant inicio, Instant fim, Long usuarioId, AcaoCritica acao, int pagina, int tamanho) {

    /** Uma página não traz mais que isto, mesmo se pedir. */
    public static final int TAMANHO_MAXIMO = 100;

    public FiltroDoLog {
        if (inicio != null && fim != null && !inicio.isBefore(fim)) {
            throw new IllegalArgumentException("O início do período precisa ser anterior ao fim");
        }
        if (usuarioId != null && usuarioId <= 0) {
            throw new IllegalArgumentException("O usuário do filtro precisa ser um identificador válido");
        }
        if (pagina < 0) {
            throw new IllegalArgumentException("A página começa em zero");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("O tamanho da página precisa estar entre 1 e " + TAMANHO_MAXIMO);
        }
    }

    /** Primeira página do log inteiro, sem filtro. */
    public static FiltroDoLog tudo() {
        return new FiltroDoLog(null, null, null, null, 0, TAMANHO_MAXIMO);
    }

    /** Quantas linhas pular antes da página. */
    public long deslocamento() {
        return (long) pagina * tamanho;
    }
}
