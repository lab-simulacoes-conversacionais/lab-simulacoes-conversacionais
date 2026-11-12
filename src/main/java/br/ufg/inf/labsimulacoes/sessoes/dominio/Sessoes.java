package br.ufg.inf.labsimulacoes.sessoes.dominio;

import java.util.Optional;

/** Porta de persistência das sessões no PostgreSQL, a fonte da verdade (Stack, seção 5). */
public interface Sessoes {

    Optional<Sessao> buscar(long id);

    /** Trava a sessão até o fim da transação, para dois controles simultâneos não se sobreporem. */
    Optional<Sessao> buscarParaAlterar(long id);

    /** RN-10: só conta a sessão com status Em andamento; pausada e encerrada não contam. */
    boolean temSessaoEmAndamento(long turmaId);

    /**
     * Grava a sessão nova ou a mudança de estado.
     *
     * @throws TurmaComSessaoEmAndamento se o índice único do RN-10 recusar, quando duas sessões são
     *     iniciadas ou retomadas na mesma turma ao mesmo tempo
     */
    Sessao salvar(Sessao sessao);

    /** Grava pausa, retomada ou reinício em evento_sessao. */
    void registrar(long sessaoId, EventoDaSessao evento);

    /**
     * Grava uma pergunta da turma ou uma resposta da IA, com a autoria genérica (RN-11) e o horário da
     * mensagem, que vem da aplicação.
     *
     * @param respostaIaMetaId metadados da resposta em resposta_ia_meta (RNF-10); obrigatório na resposta
     *     da IA e ausente na pergunta da turma
     */
    void registrarMensagem(long sessaoId, MensagemExibida mensagem, Long respostaIaMetaId);

    /**
     * Marca a pergunta da turma cuja resposta foi bloqueada (RN-20). A pergunta é a que estava sendo
     * respondida, e não a última da sessão: se a sessão for reiniciada durante a resposta, outra pergunta
     * pode entrar antes do bloqueio (RN-19). Ela é achada pelo texto e pelo horário com que foi lida do
     * banco; se houver duas iguais, vale a de maior id.
     *
     * @param pergunta a pergunta pendente, como veio de {@link #historico}
     * @return se a pergunta foi encontrada e marcada
     */
    boolean marcarRespostaBloqueada(long sessaoId, MensagemExibida pergunta);

    /** Tudo o que reconstrói o estado quente da sessão, com até {@code mensagens} mensagens. */
    Optional<HistoricoDaSessao> historico(long sessaoId, int mensagens);
}
