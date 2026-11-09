package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

public class HabilidadesForaDoCatalogo extends RuntimeException {

    public HabilidadesForaDoCatalogo(List<String> codigos) {
        super("Estas habilidades não estão no catálogo da BNCC: " + String.join(", ", codigos));
    }
}
