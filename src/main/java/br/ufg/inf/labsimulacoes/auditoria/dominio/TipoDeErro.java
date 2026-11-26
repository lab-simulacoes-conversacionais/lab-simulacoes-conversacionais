package br.ufg.inf.labsimulacoes.auditoria.dominio;

/** O que a pessoa acha que está errado na resposta (RN-21). */
public enum TipoDeErro {
    ERRO_HISTORICO("Erro histórico"),
    ERRO_CIENTIFICO("Erro científico"),
    ANACRONISMO("Anacronismo"),
    SAIDA_DO_PERSONAGEM("Saída do personagem"),
    CONTEUDO_INADEQUADO("Conteúdo inadequado");

    private final String rotulo;

    TipoDeErro(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Como o tipo aparece na tela. */
    public String rotulo() {
        return rotulo;
    }
}
