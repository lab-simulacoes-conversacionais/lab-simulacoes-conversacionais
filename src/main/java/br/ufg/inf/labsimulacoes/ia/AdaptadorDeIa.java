package br.ufg.inf.labsimulacoes.ia;

import java.util.function.Consumer;

/**
 * Porta única para o provedor de IA (Stack, seção 5). Os outros módulos falam só com esta interface e
 * nunca com o Spring AI, então trocar de provedor ou de modelo exige só configuração (RNF-02).
 *
 * <p>Cada resposta gerada tem os metadados gravados em resposta_ia_meta (RNF-10, RNF-13), inclusive
 * as que depois forem bloqueadas pela moderação. Quem exibe a resposta guarda o {@code metadadosId}
 * junto com ela. A moderação não gera metadados.
 *
 * <p>Exige o perfil Professor ou Curador: o administrador não usa conteúdo pedagógico (RN-37).
 */
public interface AdaptadorDeIa {

    /**
     * Fala do personagem em resposta à pergunta da turma. Os trechos chegam a {@code aoReceberTrecho}
     * na ordem e na mesma thread, enquanto o modelo gera, e o método só volta quando a resposta termina.
     * A resposta não passa pela moderação aqui: agrupar por frase e moderar é de quem exibe (RN-20).
     *
     * <p>Uma exceção lançada por {@code aoReceberTrecho} interrompe a geração e chega a quem chamou
     * sem mudança; nesse caso, nenhum metadado é gravado.
     *
     * <p>Uma resposta sem texto, como quando o raciocínio gasta todos os tokens, não é exibida: vira
     * {@link FalhaNoProvedorDeIa}, mas os metadados são gravados antes, porque houve consumo (RNF-13).
     *
     * @throws IllegalArgumentException se faltar ao cenário um campo que o template do personagem usa;
     *     num cenário aprovado, todos existem (RN-02, RN-03)
     * @throws FalhaNoProvedorDeIa se o provedor falhar, ficar fora do ar ou devolver uma resposta vazia
     */
    RespostaDoPersonagem responder(PedidoDeFala pedido, Consumer<String> aoReceberTrecho);

    /**
     * Ponto de partida para um cenário novo (RN-04). Quem chama salva o resultado como Rascunho marcado
     * "Sugerido por IA"; a IA nunca aprova nem altera um cenário.
     *
     * @throws FalhaNoProvedorDeIa se o provedor falhar ou devolver algo que não vira um roteiro
     */
    SugestaoDeCenario sugerirCenario(PedidoDeSugestao pedido);

    /**
     * Confere um texto pelo modelo de moderação do provedor (RN-20). Decidir o que fazer com um texto
     * sinalizado é de quem chama.
     *
     * @throws IllegalArgumentException se o texto estiver vazio
     * @throws FalhaNoProvedorDeIa se o provedor falhar ou ficar fora do ar
     */
    ResultadoDaModeracao moderar(String texto);
}
