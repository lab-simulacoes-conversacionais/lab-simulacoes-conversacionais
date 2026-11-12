package br.ufg.inf.labsimulacoes.sessoes.dominio;

/**
 * O filtro de conteúdo bloqueou a pergunta da turma (RN-20). Ela não foi gravada como mensagem nem
 * enviada ao modelo; a sessão já foi pausada e o caso foi para a fila do curador.
 */
public class PerguntaBloqueada extends RuntimeException {

    public PerguntaBloqueada(long sessaoId) {
        super("O filtro de conteúdo bloqueou a pergunta da sessão " + sessaoId);
    }
}
