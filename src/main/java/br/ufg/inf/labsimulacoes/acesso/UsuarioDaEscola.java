package br.ufg.inf.labsimulacoes.acesso;

/**
 * Um profissional como os outros módulos o veem: só o nome, para mostrar quem fez uma ação. Sem e-mail,
 * senha nem perfis.
 *
 * @param ativo um usuário desativado continua aparecendo nas ações que fez antes
 */
public record UsuarioDaEscola(long id, String nome, boolean ativo) {}
