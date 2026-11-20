package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.ArrayList;
import java.util.List;

/**
 * RN-06: falta conferir o checklist ou testar o cenário antes de aprovar. Diz tudo o que falta de uma
 * vez, para o curador resolver numa volta só.
 */
public class AprovacaoIncompleta extends RuntimeException {

    private final transient ConferenciaDaCuradoria conferencia;

    public AprovacaoIncompleta(ConferenciaDaCuradoria conferencia) {
        super(mensagem(conferencia));
        this.conferencia = conferencia;
    }

    public List<ItemDoChecklist> itensFaltando() {
        return conferencia.itensFaltando();
    }

    public int perguntasFaltando() {
        return conferencia.perguntasFaltando();
    }

    private static String mensagem(ConferenciaDaCuradoria conferencia) {
        var faltas = new ArrayList<String>();
        conferencia.itensFaltando().forEach(item -> faltas.add(item.descricao()));
        if (conferencia.perguntasFaltando() > 0) {
            faltas.add("testar com pelo menos " + PerguntasDeTeste.MINIMO_PARA_APROVAR + " perguntas (feitas: "
                    + conferencia.perguntasDeTeste() + ")");
        }
        return "Para aprovar, falta: " + String.join("; ", faltas) + " (RN-06)";
    }
}
