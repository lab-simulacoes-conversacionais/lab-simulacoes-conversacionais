package br.ufg.inf.labsimulacoes.auditoria.dominio;

/**
 * A gravidade que o curador dá à sinalização confirmada na triagem (RF-24, RN-22). Usa os mesmos níveis da
 * {@link GravidadePercebida}, mas é a que vale para a suspensão do cenário (RN-23) e para a taxa de erros
 * (RN-29).
 */
public enum GravidadeFinal {
    LEVE("Leve", "Imprecisão que não afeta o objetivo"),
    MODERADA("Moderada", "Erro que pode levar a uma compreensão incorreta"),
    GRAVE("Grave", "Erro sobre o conceito central do objetivo BNCC ou conteúdo inadequado à faixa etária");

    private final String rotulo;
    private final String descricao;

    GravidadeFinal(String rotulo, String descricao) {
        this.rotulo = rotulo;
        this.descricao = descricao;
    }

    /** Como a gravidade aparece na tela. */
    public String rotulo() {
        return rotulo;
    }

    /** O critério do RN-22, para o curador escolher o nível. */
    public String descricao() {
        return descricao;
    }
}
