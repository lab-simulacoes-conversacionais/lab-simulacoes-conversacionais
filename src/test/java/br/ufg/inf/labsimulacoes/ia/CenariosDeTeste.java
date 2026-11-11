package br.ufg.inf.labsimulacoes.ia;

import java.util.List;

/** Cenário completo, como chega de uma versão aprovada (RN-02, RN-03). */
public final class CenariosDeTeste {

    public static final CenarioDaConversa VOO_DO_14_BIS = new CenarioDaConversa(
            "O voo do 14-bis", "Santos Dumont", "Inventor brasileiro, em primeira pessoa", "Paris, 1906",
            List.of("Explicar por que o voo por meios próprios foi importante"), List.of("Política partidária"),
            List.of("Museu Aeroespacial"), "História", 8, List.of("EF08HI03"), "13 a 14 anos");

    private CenariosDeTeste() {}
}
