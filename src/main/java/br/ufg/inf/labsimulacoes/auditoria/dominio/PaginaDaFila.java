package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Uma página da fila de triagem (RF-24) e, se houver mais sinalizações pendentes, o cursor de onde a
 * próxima continua.
 *
 * @param sinalizacoes da mais antiga para a mais recente
 * @param proxima cursor da próxima página; nulo quando esta é a última
 */
public record PaginaDaFila(List<SinalizacaoPendente> sinalizacoes, CursorDaFila proxima) {

    public static final int TAMANHO_MAXIMO = 100;

    public PaginaDaFila {
        sinalizacoes = List.copyOf(sinalizacoes);
    }

    /** @throws IllegalArgumentException se o tamanho estiver fora de 1 a {@value #TAMANHO_MAXIMO} */
    public static void exigirTamanho(int tamanho) {
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("O tamanho da página precisa estar entre 1 e " + TAMANHO_MAXIMO);
        }
    }

    /**
     * Monta a página a partir de uma leitura que pediu uma sinalização a mais que o tamanho. Se essa
     * sinalização extra veio, há próxima página, e ela fica de fora desta.
     */
    public static PaginaDaFila lidaComUmaAMais(List<SinalizacaoPendente> lidas, int tamanho) {
        if (lidas.size() <= tamanho) {
            return new PaginaDaFila(lidas, null);
        }
        List<SinalizacaoPendente> daPagina = lidas.subList(0, tamanho);
        return new PaginaDaFila(daPagina, CursorDaFila.apos(daPagina.get(tamanho - 1)));
    }

    public boolean temProxima() {
        return proxima != null;
    }

    public Optional<CursorDaFila> cursorDaProxima() {
        return Optional.ofNullable(proxima);
    }
}
