package br.ufg.inf.labsimulacoes.acesso.dominio;

/** A turma já tem sessões, e apagá-la tiraria do histórico e das métricas o que ela fez (RN-31). */
public class TurmaEmUso extends RuntimeException {

    public TurmaEmUso(long id) {
        super("A turma " + id + " já tem sessões e não pode ser excluída");
    }
}
