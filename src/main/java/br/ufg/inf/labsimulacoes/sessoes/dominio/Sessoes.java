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

    /** Tudo o que reconstrói o estado quente da sessão, com até {@code mensagens} mensagens. */
    Optional<HistoricoDaSessao> historico(long sessaoId, int mensagens);
}
