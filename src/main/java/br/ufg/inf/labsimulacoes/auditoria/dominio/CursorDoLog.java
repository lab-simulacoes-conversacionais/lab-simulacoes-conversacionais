package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;

/**
 * Posição da última linha de uma página do log, de onde a página seguinte continua (RF-26). A
 * próxima página traz só as linhas que vêm depois desta na ordem da consulta, da mais recente para
 * a mais antiga. Assim, uma ação gravada enquanto alguém lê o log não empurra as linhas para a
 * página seguinte, como aconteceria contando linhas a pular.
 *
 * @param ocorridoEm quando ocorreu a última ação da página
 * @param id identificador da última linha da página, que desempata as ações com a mesma data
 */
public record CursorDoLog(Instant ocorridoEm, long id) {

    public CursorDoLog {
        if (ocorridoEm == null) {
            throw new IllegalArgumentException("O cursor precisa da data da última ação da página");
        }
        if (id <= 0) {
            throw new IllegalArgumentException("O cursor precisa de uma linha já gravada");
        }
    }

    /** Continua a consulta depois desta linha. */
    public static CursorDoLog apos(RegistroDeAuditoria linha) {
        if (linha.id() == null) {
            throw new IllegalArgumentException("O cursor precisa de uma linha já gravada");
        }
        return new CursorDoLog(linha.ocorridoEm(), linha.id());
    }
}
