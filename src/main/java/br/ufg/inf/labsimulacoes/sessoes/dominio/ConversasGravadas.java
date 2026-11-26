package br.ufg.inf.labsimulacoes.sessoes.dominio;

import br.ufg.inf.labsimulacoes.sessoes.MensagemDaRevisao;
import br.ufg.inf.labsimulacoes.sessoes.TrechoParaSinalizar;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** A conversa gravada no PostgreSQL, inteira, para a revisão e a sinalização de erro (RF-23). */
public interface ConversasGravadas {

    /**
     * Ids das sessões com IA do professor encerradas desde o momento dado, ou que começaram desde ele e
     * ainda estão abertas; da mais recente para a mais antiga.
     */
    List<Long> sessoesDoProfessorDesde(long professorId, Instant desde);

    /** Todas as mensagens da sessão, inclusive as de antes de um reinício, em ordem. */
    List<MensagemDaRevisao> mensagens(long sessaoId);

    /** A resposta da IA com a pergunta da turma logo antes dela; vazio se não for uma resposta da IA. */
    Optional<TrechoParaSinalizar> resposta(long mensagemId);
}
