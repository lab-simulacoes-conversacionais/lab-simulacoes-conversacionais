package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * Quem decide sobre um cenário em curadoria. O perfil vem junto porque o próprio cenário confere o
 * RN-05, e não só o caso de uso.
 *
 * @param id usuário que decide
 * @param curador se o usuário tem o perfil Curador
 */
public record Revisor(long id, boolean curador) {}
