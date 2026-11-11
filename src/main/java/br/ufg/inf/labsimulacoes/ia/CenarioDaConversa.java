package br.ufg.inf.labsimulacoes.ia;

import java.util.List;

/**
 * O que o adaptador precisa da versão do cenário para montar as instruções do personagem. Quem chama
 * copia os campos da versão aprovada; o adaptador não depende do módulo de cenários.
 *
 * @param papelDaIa como a IA deve agir no papel do personagem
 * @param limitesDeAssunto o que a IA não deve abordar (RN-03, RN-15)
 * @param ano ano escolar, de 1 a 9
 * @param habilidades códigos BNCC vinculados (RN-02)
 */
public record CenarioDaConversa(
        String titulo,
        String personagem,
        String papelDaIa,
        String contexto,
        List<String> objetivos,
        List<String> limitesDeAssunto,
        List<String> fontes,
        String componente,
        Integer ano,
        List<String> habilidades,
        String faixaEtaria) {

    public CenarioDaConversa {
        titulo = Textos.obrigatorio(titulo, "título");
        personagem = Textos.obrigatorio(personagem, "personagem");
        papelDaIa = Textos.opcional(papelDaIa);
        contexto = Textos.opcional(contexto);
        objetivos = Textos.itens(objetivos);
        limitesDeAssunto = Textos.itens(limitesDeAssunto);
        fontes = Textos.itens(fontes);
        componente = Textos.opcional(componente);
        habilidades = Textos.itens(habilidades);
        faixaEtaria = Textos.opcional(faixaEtaria);
    }
}
