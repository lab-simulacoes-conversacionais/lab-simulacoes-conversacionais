package br.ufg.inf.labsimulacoes.ia;

import java.util.List;

/**
 * Conteúdo de um cenário sugerido pela IA, nos mesmos campos do roteiro do cenário (RF-06). Qualquer
 * campo pode vir vazio; o curador confere e completa (RN-04).
 *
 * @param duracaoPrevistaMin em minutos
 */
public record RoteiroSugerido(
        String titulo,
        String personagem,
        String papelDaIa,
        String contexto,
        List<String> objetivos,
        List<String> limitesDeAssunto,
        List<String> fontes,
        List<String> perguntasDisparadoras,
        String faixaEtaria,
        Integer duracaoPrevistaMin) {

    public RoteiroSugerido {
        titulo = Textos.opcional(titulo);
        personagem = Textos.opcional(personagem);
        papelDaIa = Textos.opcional(papelDaIa);
        contexto = Textos.opcional(contexto);
        objetivos = Textos.itens(objetivos);
        limitesDeAssunto = Textos.itens(limitesDeAssunto);
        fontes = Textos.itens(fontes);
        perguntasDisparadoras = Textos.itens(perguntasDisparadoras);
        faixaEtaria = Textos.opcional(faixaEtaria);
    }
}
