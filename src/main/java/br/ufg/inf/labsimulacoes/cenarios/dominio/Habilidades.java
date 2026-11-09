package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

/** Porta de persistência do catálogo da BNCC. A implementação fica na infraestrutura. */
public interface Habilidades {

    /**
     * Insere as habilidades novas e atualiza as que mudaram, pelo código. Nunca apaga: as versões de
     * cenário apontam para as habilidades (RN-02), inclusive as que saírem de uma versão da BNCC.
     */
    ResultadoDaGravacao gravar(List<HabilidadeBncc> habilidades);

    /** Em ordem de código, até o limite do filtro. */
    List<HabilidadeBncc> buscar(FiltroDeHabilidades filtro);
}
