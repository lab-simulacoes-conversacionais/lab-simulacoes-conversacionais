package br.ufg.inf.labsimulacoes.sessoes.dominio;

/**
 * Resultado de pausar, retomar ou reiniciar: a sessão depois da mudança e o evento a gravar.
 *
 * @param sessao como ficou
 * @param evento o que aconteceu, para evento_sessao e para o estado no Redis
 */
public record MudancaNaSessao(Sessao sessao, EventoDaSessao evento) {}
