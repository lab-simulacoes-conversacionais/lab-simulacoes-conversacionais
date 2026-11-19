package br.ufg.inf.labsimulacoes.ia;

/**
 * A sugestão de cenário está desligada porque o ambiente não definiu o modelo dela. Diferente de
 * {@link FalhaNoProvedorDeIa}, tentar de novo não resolve: é preciso definir a variável
 * IA_MODELO_SUGESTAO e reiniciar a aplicação. O chat continua funcionando.
 */
public class SugestaoDesligada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SugestaoDesligada() {
        super("A sugestão de cenário está desligada: defina a variável IA_MODELO_SUGESTAO com o modelo da sugestão");
    }
}
