package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Uma página do acervo: cenários de qualquer status, cada um com a versão mais recente e a última
 * devolução dela (RN-37). Vale para o acervo do curador, com todos os autores, e para os cenários de um
 * só autor.
 *
 * <p>Quando um cenário aprovado ganha uma versão nova, a mais recente ainda não foi aprovada. Por isso a
 * página traz também a última versão aprovada desses cenários: as listas mostram o que foi aprovado e
 * avisam da versão nova, e a fila da curadoria mostra a nova, que é a que está em revisão (RN-08).
 *
 * @param itens os cenários da página, na ordem da consulta, cada um com a versão mais recente
 * @param ultimasAprovadas pelo id do cenário, a última versão aprovada dos cenários em que ela não é a
 *     mais recente
 * @param temProxima se há cenários depois desta página
 */
public record TrechoDoAcervo(List<Cenario> itens, Map<Long, VersaoDoCenario> ultimasAprovadas, boolean temProxima) {

    public TrechoDoAcervo {
        itens = List.copyOf(itens);
        ultimasAprovadas = Map.copyOf(ultimasAprovadas);
    }

    /** Página em que nenhum cenário tem versão nova além da última aprovada. */
    public TrechoDoAcervo(List<Cenario> itens, boolean temProxima) {
        this(itens, Map.of(), temProxima);
    }

    /**
     * A última versão aprovada do cenário, se ele tem uma versão mais nova que ela. Vazio quando a mais
     * recente já é a aprovada ou quando o cenário nunca foi aprovado.
     */
    public Optional<VersaoDoCenario> ultimaAprovada(Cenario cenario) {
        return Optional.ofNullable(ultimasAprovadas.get(cenario.id()));
    }
}
