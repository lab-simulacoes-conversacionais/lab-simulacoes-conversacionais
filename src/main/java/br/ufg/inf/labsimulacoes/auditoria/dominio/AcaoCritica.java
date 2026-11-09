package br.ufg.inf.labsimulacoes.auditoria.dominio;

/** Ações que o RN-35 manda registrar no log, cada uma com o tipo de entidade que ela afeta. */
public enum AcaoCritica {
    APROVAR_CENARIO("cenario"),
    DEVOLVER_CENARIO("cenario"),
    SUSPENDER_CENARIO("cenario"),
    REATIVAR_CENARIO("cenario"),
    ARQUIVAR_CENARIO("cenario"),
    TRIAR_SINALIZACAO("sinalizacao"),
    ALTERAR_PERFIS("usuario"),
    EXPORTAR_RELATORIO("relatorio");

    private final String entidade;

    AcaoCritica(String entidade) {
        this.entidade = entidade;
    }

    public String entidade() {
        return entidade;
    }
}
