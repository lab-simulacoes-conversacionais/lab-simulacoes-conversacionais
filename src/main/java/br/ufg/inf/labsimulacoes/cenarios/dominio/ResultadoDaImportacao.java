package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * Resumo de uma importação do catálogo da BNCC.
 *
 * @param inseridas habilidades que ainda não estavam no banco
 * @param atualizadas habilidades que já estavam no banco e mudaram
 * @param inalteradas habilidades que já estavam no banco e continuam iguais
 * @param ignoradas habilidades do arquivo que não estão vigentes
 */
public record ResultadoDaImportacao(int inseridas, int atualizadas, int inalteradas, int ignoradas) {}
