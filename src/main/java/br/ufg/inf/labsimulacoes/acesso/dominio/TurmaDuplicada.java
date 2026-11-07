package br.ufg.inf.labsimulacoes.acesso.dominio;

/** Já existe uma turma com a mesma escola, ano/série, turno e rótulo. */
public class TurmaDuplicada extends RuntimeException {

    public TurmaDuplicada(Turma turma) {
        super("Já existe a turma %s, %s, %s, %s"
                .formatted(turma.rotulo(), turma.anoSerie(), turma.turno(), turma.escola()));
    }
}
