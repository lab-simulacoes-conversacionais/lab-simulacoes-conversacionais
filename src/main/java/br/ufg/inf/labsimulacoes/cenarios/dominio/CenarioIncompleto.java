package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;
import java.util.stream.Collectors;

public class CenarioIncompleto extends RuntimeException {

    private final transient List<PendenciaDoCenario> pendencias;

    public CenarioIncompleto(List<PendenciaDoCenario> pendencias) {
        super("O cenário não pode ir à curadoria. Falta: "
                + pendencias.stream().map(PendenciaDoCenario::descricao).collect(Collectors.joining(", ")));
        this.pendencias = List.copyOf(pendencias);
    }

    public List<PendenciaDoCenario> pendencias() {
        return pendencias;
    }
}
