package br.ufg.inf.labsimulacoes.sessoes.dominio;

/**
 * A pergunta da turma passou do tamanho máximo. O campo da tela já limita, então isso só acontece
 * numa requisição feita fora dela; nada é conferido, moderado nem gravado.
 */
public class PerguntaLongaDemais extends RuntimeException {

    public PerguntaLongaDemais(int maximo) {
        super("A pergunta da turma tem no máximo " + maximo + " caracteres");
    }
}
