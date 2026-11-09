package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

/**
 * Buscas no catálogo da BNCC guardadas em cache. O catálogo só muda quando o importador roda, então
 * a mesma busca pode ser atendida sem ir ao banco até a próxima importação.
 */
public interface BuscasDoCatalogo {

    List<HabilidadeBncc> buscar(FiltroDeHabilidades filtro);

    /** Descarta todas as buscas guardadas, para que a próxima leia o catálogo atualizado. */
    void esquecerTudo();
}
