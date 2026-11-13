package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

/**
 * Uma página do acervo: cenários de qualquer status, cada um com a versão mais recente e a última
 * devolução dela (RN-37). Vale para o acervo do curador, com todos os autores, e para os cenários de um
 * só autor.
 *
 * @param itens os cenários da página, na ordem da consulta
 * @param temProxima se há cenários depois desta página
 */
public record TrechoDoAcervo(List<Cenario> itens, boolean temProxima) {

    public TrechoDoAcervo {
        itens = List.copyOf(itens);
    }
}
