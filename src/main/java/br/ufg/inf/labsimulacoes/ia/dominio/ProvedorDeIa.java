package br.ufg.inf.labsimulacoes.ia.dominio;

import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.ia.RoteiroSugerido;
import java.util.function.Consumer;

/**
 * Porta para o provedor de IA. A implementação fica na infraestrutura e é a única parte do sistema que
 * conhece o Spring AI (RNF-02). Falhas do provedor chegam como
 * {@link br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa}.
 */
public interface ProvedorDeIa {

    /** Nome do provedor, gravado nos metadados, como "openai". */
    String nome();

    /**
     * Gera a resposta em streaming. Cada trecho chega a {@code aoReceberTrecho} assim que é recebido;
     * uma exceção lançada por ele interrompe o streaming e chega a quem chamou sem mudança.
     *
     * @return o texto completo
     */
    Geracao<String> conversar(PromptMontado prompt, Consumer<String> aoReceberTrecho);

    /** Gera um roteiro de cenário em formato estruturado. */
    Geracao<RoteiroSugerido> sugerirRoteiro(PromptMontado prompt);

    ResultadoDaModeracao moderar(String texto);
}
