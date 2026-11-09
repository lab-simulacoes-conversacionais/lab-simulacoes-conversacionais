package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

/** Conteúdo que atende ao RN-02 e ao RN-03, para os testes tirarem só o campo que interessa. */
public final class ConteudosDeTeste {

    public static final RoteiroDoCenario ROTEIRO = new RoteiroDoCenario(
            "Santos Dumont",
            "Responder em primeira pessoa como o inventor",
            "Paris, 1906, dias depois do voo do 14-bis",
            List.of("Explicar por que o voo por meios próprios foi importante"),
            List.of("Política partidária"),
            List.of("Museu Aeroespacial"),
            List.of("Por que o 14-bis foi tão importante?"));

    public static final VinculoCurricular CURRICULO =
            new VinculoCurricular("História", "EF", 8, List.of("EF08HI03"));

    private ConteudosDeTeste() {}

    public static ConteudoDoCenario completo() {
        return new ConteudoDoCenario("O voo do 14-bis", ROTEIRO, CURRICULO, "13 a 14 anos", 20);
    }

    public static ConteudoDoCenario soComTitulo() {
        return new ConteudoDoCenario("O voo do 14-bis", null, null, null, null);
    }
}
