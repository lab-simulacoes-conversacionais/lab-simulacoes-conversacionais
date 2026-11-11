package br.ufg.inf.labsimulacoes.ia.dominio;

import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.PedidoDeSugestao;

/**
 * Junta o template de prompt com os dados do pedido (Stack, seção 5). Os dados do cenário vão nas
 * instruções; tudo o que é digitado por professor ou turma vai em mensagens de usuário (RNF-09).
 */
public interface MontadorDePrompt {

    PromptMontado daFala(PedidoDeFala pedido);

    PromptMontado daSugestao(PedidoDeSugestao pedido);
}
