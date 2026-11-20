package br.ufg.inf.labsimulacoes.cenarios.dominio;

/** Os itens do checklist de curadoria que o curador confere antes de aprovar (RF-10, RN-06). */
public enum ItemDoChecklist {
    FATOS_CONFERIDOS("fatos conferidos com as fontes"),
    LINGUAGEM_ADEQUADA("linguagem adequada à faixa etária"),
    OBJETIVOS_COERENTES("objetivos coerentes com a BNCC"),
    LIMITES_RESPEITADOS("limites de assunto respeitados no teste");

    private final String descricao;

    ItemDoChecklist(String descricao) {
        this.descricao = descricao;
    }

    /** Como o item aparece na tela e nas mensagens. */
    public String descricao() {
        return descricao;
    }
}
