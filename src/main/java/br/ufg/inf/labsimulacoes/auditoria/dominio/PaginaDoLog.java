package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Uma página da consulta do log (RF-26) e, se houver mais linhas, o cursor de onde a próxima
 * continua.
 *
 * @param linhas da ação mais recente para a mais antiga
 * @param proxima cursor da próxima página; nulo quando esta é a última
 */
public record PaginaDoLog(List<RegistroDeAuditoria> linhas, CursorDoLog proxima) {

    public PaginaDoLog {
        linhas = List.copyOf(linhas);
    }

    /**
     * Monta a página a partir de uma leitura que pediu uma linha a mais que o tamanho. Se essa linha
     * extra veio, há próxima página, e ela fica de fora desta.
     */
    public static PaginaDoLog lidaComUmaAMais(List<RegistroDeAuditoria> lidas, int tamanho) {
        if (lidas.size() <= tamanho) {
            return new PaginaDoLog(lidas, null);
        }
        List<RegistroDeAuditoria> daPagina = lidas.subList(0, tamanho);
        return new PaginaDoLog(daPagina, CursorDoLog.apos(daPagina.get(tamanho - 1)));
    }

    public boolean temProxima() {
        return proxima != null;
    }

    public Optional<CursorDoLog> cursorDaProxima() {
        return Optional.ofNullable(proxima);
    }
}
