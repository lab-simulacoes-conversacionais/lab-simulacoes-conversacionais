package br.ufg.inf.labsimulacoes.acesso.dominio;

/**
 * Turma que participa das sessões (RF-04).
 *
 * <p>RN-32: a turma é identificada só por escola, ano/série, turno e rótulo. Não há lista de
 * estudantes nem qualquer campo sobre eles; o número de presentes é informado em cada sessão.
 */
public record Turma(Long id, String escola, String anoSerie, String turno, String rotulo) {

    public Turma {
        escola = preenchido(escola, "escola");
        anoSerie = preenchido(anoSerie, "ano/série");
        turno = preenchido(turno, "turno");
        rotulo = preenchido(rotulo, "rótulo");
    }

    public static Turma nova(String escola, String anoSerie, String turno, String rotulo) {
        return new Turma(null, escola, anoSerie, turno, rotulo);
    }

    /** Mantém o identificador, para que as sessões já feitas continuem ligadas à mesma turma. */
    public Turma alterar(String escola, String anoSerie, String turno, String rotulo) {
        return new Turma(id, escola, anoSerie, turno, rotulo);
    }

    /** Duas turmas com a mesma identificação seriam indistinguíveis ao iniciar uma sessão. */
    public boolean mesmaIdentificacaoQue(Turma outra) {
        return escola.equals(outra.escola)
                && anoSerie.equals(outra.anoSerie)
                && turno.equals(outra.turno)
                && rotulo.equals(outra.rotulo);
    }

    /** Remove os espaços das pontas, para que "8º A " e "8º A" não virem turmas diferentes. */
    private static String preenchido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório");
        }
        return valor.strip();
    }
}
