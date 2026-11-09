package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

/**
 * Conteúdo textual de uma versão do cenário (RF-06), guardado em JSONB (Stack, seção 6).
 *
 * <p>No rascunho tudo pode faltar. O que é obrigatório para ir à curadoria está em
 * {@link PendenciaDoCenario}.
 *
 * @param papelDaIa como a IA deve agir no papel do personagem
 * @param limitesDeAssunto o que a IA não deve abordar (RN-03)
 * @param perguntasDisparadoras opcionais: o RN-03 não as exige
 */
public record RoteiroDoCenario(
        String personagem,
        String papelDaIa,
        String contexto,
        List<String> objetivos,
        List<String> limitesDeAssunto,
        List<String> fontes,
        List<String> perguntasDisparadoras) {

    public RoteiroDoCenario {
        personagem = Textos.opcional(personagem);
        papelDaIa = Textos.opcional(papelDaIa);
        contexto = Textos.opcional(contexto);
        objetivos = Textos.itens(objetivos);
        limitesDeAssunto = Textos.itens(limitesDeAssunto);
        fontes = Textos.itens(fontes);
        perguntasDisparadoras = Textos.itens(perguntasDisparadoras);
    }

    static RoteiroDoCenario vazio() {
        return new RoteiroDoCenario(null, null, null, null, null, null, null);
    }
}
