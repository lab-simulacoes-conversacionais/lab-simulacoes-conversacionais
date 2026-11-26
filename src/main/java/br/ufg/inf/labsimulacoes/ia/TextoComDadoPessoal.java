package br.ufg.inf.labsimulacoes.ia;

import java.util.Objects;

/**
 * O texto digitado tem um padrão de dado pessoal e não foi enviado ao provedor (RN-13, RNF-01). Leva só
 * o tipo encontrado: nem a mensagem nem nenhum campo repetem o texto, para ele não parar em log, tela
 * ou métrica. Quem chama mostra o {@link #aviso(String)} e deixa o professor reescrever.
 */
public class TextoComDadoPessoal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final TipoDeDadoPessoal tipo;

    public TextoComDadoPessoal(TipoDeDadoPessoal tipo) {
        super("O texto tem um dado pessoal do tipo " + Objects.requireNonNull(tipo, "tipo").descricao()
                + " e não foi enviado ao provedor");
        this.tipo = tipo;
    }

    public TipoDeDadoPessoal tipo() {
        return tipo;
    }

    /**
     * O aviso ao professor, que nomeia o tipo sem repetir o dado.
     *
     * @param campo o que foi digitado, com o artigo, como "A pergunta" ou "O tema"
     */
    public String aviso(String campo) {
        return campo + " parece ter um dado pessoal (" + tipo.descricao()
                + "). Reescreva sem e-mail, telefone, CPF ou endereço, e não digite nomes de estudantes.";
    }
}
