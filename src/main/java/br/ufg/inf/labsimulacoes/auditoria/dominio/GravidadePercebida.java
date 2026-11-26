package br.ufg.inf.labsimulacoes.auditoria.dominio;

/**
 * A gravidade que quem sinaliza percebe, nos mesmos níveis que o curador usa na triagem (RN-21, RN-22).
 * A gravidade final é do curador e vem com a triagem (RF-24).
 */
public enum GravidadePercebida {
    LEVE("Leve", "Imprecisão que não afeta o objetivo"),
    MODERADA("Moderada", "Erro que pode levar a uma compreensão incorreta"),
    GRAVE("Grave", "Erro sobre o conceito central ou conteúdo inadequado à faixa etária");

    private final String rotulo;
    private final String descricao;

    GravidadePercebida(String rotulo, String descricao) {
        this.rotulo = rotulo;
        this.descricao = descricao;
    }

    /** Como a gravidade aparece na tela. */
    public String rotulo() {
        return rotulo;
    }

    /** O critério do RN-22, para quem sinaliza escolher o nível. */
    public String descricao() {
        return descricao;
    }
}
