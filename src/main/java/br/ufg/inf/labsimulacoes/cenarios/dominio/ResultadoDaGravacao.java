package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * Quantas habilidades a gravação inseriu e quantas atualizou. As que já estavam iguais no banco não
 * entram em nenhuma das duas contas.
 */
public record ResultadoDaGravacao(int inseridas, int atualizadas) {}
