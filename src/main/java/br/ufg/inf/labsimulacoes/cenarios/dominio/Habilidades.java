package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Porta de persistência do catálogo da BNCC. A implementação fica na infraestrutura. */
public interface Habilidades {

    /**
     * Insere as habilidades novas e atualiza as que mudaram, pelo código. Nunca apaga: as versões de
     * cenário apontam para as habilidades (RN-02), inclusive as que saírem de uma versão da BNCC.
     */
    ResultadoDaGravacao gravar(List<HabilidadeBncc> habilidades);

    /** Em ordem de código, até o limite do filtro. */
    List<HabilidadeBncc> buscar(FiltroDeHabilidades filtro);

    /** Quais destes códigos estão no catálogo. */
    Set<String> existentes(Collection<String> codigos);

    /**
     * RN-02: confere os códigos antes de gravar. A chave estrangeira também recusaria, mas sem dizer ao
     * autor qual código está errado.
     *
     * @throws HabilidadesForaDoCatalogo com os códigos desconhecidos, na ordem pedida
     */
    default void exigirNoCatalogo(List<String> codigos) {
        Set<String> conhecidos = existentes(codigos);
        List<String> desconhecidos = codigos.stream().filter(codigo -> !conhecidos.contains(codigo)).toList();
        if (!desconhecidos.isEmpty()) {
            throw new HabilidadesForaDoCatalogo(desconhecidos);
        }
    }
}
