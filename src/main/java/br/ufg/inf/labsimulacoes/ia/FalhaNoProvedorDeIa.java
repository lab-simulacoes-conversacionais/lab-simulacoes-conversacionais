package br.ufg.inf.labsimulacoes.ia;

/**
 * O provedor de IA falhou, ficou fora do ar ou devolveu algo inutilizável. Quem chama mostra um aviso
 * neutro e deixa o professor tentar de novo; a mensagem não traz a chave nem o pedido.
 */
public class FalhaNoProvedorDeIa extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FalhaNoProvedorDeIa(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
