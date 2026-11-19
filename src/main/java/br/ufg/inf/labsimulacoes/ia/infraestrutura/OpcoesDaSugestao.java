package br.ufg.inf.labsimulacoes.ia.infraestrutura;

/**
 * Opções do modelo só para a sugestão de cenário, separadas das da fala do personagem. A fala precisa
 * de latência baixa na sala (RNF-03); a sugestão é um rascunho que o professor espera alguns segundos,
 * então pode raciocinar mais e não precisa do nível de serviço prioritário, que custa mais (RNF-13).
 *
 * @param modelo o modelo da sugestão, que por padrão é o mesmo da fala
 * @param esforcoDeRaciocinio reasoning_effort da OpenAI; {@code null} usa o da configuração da fala
 * @param nivelDeServico service_tier da OpenAI; {@code null} usa o da configuração da fala
 * @param tetoDeTokens teto de tokens de saída, contando o raciocínio do modelo (RNF-13)
 */
record OpcoesDaSugestao(String modelo, String esforcoDeRaciocinio, String nivelDeServico, int tetoDeTokens) {

    OpcoesDaSugestao {
        if (modelo == null || modelo.isBlank()) {
            throw new IllegalStateException("O modelo da sugestão está vazio: confira as variáveis IA_MODELO_SUGESTAO"
                    + " e IA_MODELO, que não podem estar definidas sem valor, e a propriedade ia.sugestao.modelo");
        }
        if (tetoDeTokens < 1) {
            throw new IllegalStateException("O teto de tokens da sugestão precisa ser positivo: " + tetoDeTokens);
        }
        modelo = modelo.strip();
        esforcoDeRaciocinio = opcional(esforcoDeRaciocinio);
        nivelDeServico = opcional(nivelDeServico);
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
