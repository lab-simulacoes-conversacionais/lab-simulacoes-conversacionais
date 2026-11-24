package br.ufg.inf.labsimulacoes.ia;

import java.util.Locale;

/** Os padrões de dado pessoal que não podem ir ao provedor de IA (RN-13, RNF-01). */
public enum TipoDeDadoPessoal {
    EMAIL("e-mail"),
    TELEFONE("telefone"),
    CPF("CPF"),
    ENDERECO("endereço");

    private final String descricao;

    TipoDeDadoPessoal(String descricao) {
        this.descricao = descricao;
    }

    /** Como o tipo aparece no aviso ao professor, por exemplo "telefone". */
    public String descricao() {
        return descricao;
    }

    /** O valor da tag da métrica, por exemplo "telefone" ou "endereco". */
    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
