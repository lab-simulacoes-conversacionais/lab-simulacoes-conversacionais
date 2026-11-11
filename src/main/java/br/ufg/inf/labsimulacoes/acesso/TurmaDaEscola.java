package br.ufg.inf.labsimulacoes.acesso;

/**
 * Turma como os outros módulos a veem: só a identificação, sem lista de estudantes (RN-32).
 *
 * @param rotulo por exemplo "8º A"
 */
public record TurmaDaEscola(long id, String escola, String anoSerie, String turno, String rotulo) {}
