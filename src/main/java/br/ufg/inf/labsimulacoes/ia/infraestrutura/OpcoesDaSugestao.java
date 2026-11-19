package br.ufg.inf.labsimulacoes.ia.infraestrutura;

/**
 * Opções do modelo só para a sugestão de cenário, separadas das da fala do personagem. A fala precisa
 * de latência baixa na sala (RNF-03); a sugestão é um rascunho que o professor espera alguns segundos,
 * então pode raciocinar mais e não precisa do nível de serviço prioritário, que custa mais (RNF-13).
 *
 * @param modelo o modelo da sugestão, da variável IA_MODELO_SUGESTAO; {@code null} se ela não foi
 *     definida, e então a sugestão fica desligada e o chat continua funcionando
 * @param esforcoDeRaciocinio reasoning_effort da OpenAI; {@code null} usa o da configuração da fala
 * @param nivelDeServico service_tier da OpenAI; {@code null} usa o da configuração da fala
 * @param tetoDeTokens teto de tokens de saída, contando o raciocínio do modelo (RNF-13)
 */
record OpcoesDaSugestao(String modelo, String esforcoDeRaciocinio, String nivelDeServico, int tetoDeTokens) {

    OpcoesDaSugestao {
        if (tetoDeTokens < 1) {
            throw new IllegalStateException("O teto de tokens da sugestão precisa ser positivo: " + tetoDeTokens);
        }
        modelo = opcional(modelo);
        esforcoDeRaciocinio = opcional(esforcoDeRaciocinio);
        nivelDeServico = opcional(nivelDeServico);
    }

    /** Sem o modelo definido no ambiente, a sugestão não roda. */
    boolean ligada() {
        return modelo != null;
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
