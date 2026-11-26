package br.ufg.inf.labsimulacoes.sessoes;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Leitura das conversas gravadas, para a sinalização de erro (RF-23, RN-21) e a revisão da conversa
 * depois da aula. Lê o PostgreSQL, que guarda a conversa inteira, inclusive a de antes de um reinício.
 *
 * <p>Exige o perfil Professor ou Curador, mas não confere de quem é a sessão: quem chama decide quem
 * pode ver e sinalizar o quê (RN-37), porque o curador vê todas.
 */
public interface TrechosDasSessoes {

    /** @return vazio se a sessão não existir */
    Optional<SessaoParaRevisao> sessao(long sessaoId);

    /** Todas as mensagens da sessão, da mais antiga para a mais nova; vazia se a sessão não existir. */
    List<MensagemDaRevisao> mensagens(long sessaoId);

    /**
     * Uma resposta da IA com a pergunta da turma que a originou e os dados da sessão, para a sinalização
     * copiar (Stack, seção 6).
     *
     * @return vazio se a mensagem não existir ou não for uma resposta da IA
     */
    Optional<TrechoParaSinalizar> resposta(long mensagemId);

    /**
     * As sessões com IA do professor encerradas desde o momento dado, ou que começaram desde ele e ainda
     * não foram encerradas; da mais recente para a mais antiga. As sessões de controle ficam de fora,
     * porque não têm respostas da IA.
     */
    List<SessaoParaRevisao> doProfessorDesde(long professorId, Instant desde);
}
