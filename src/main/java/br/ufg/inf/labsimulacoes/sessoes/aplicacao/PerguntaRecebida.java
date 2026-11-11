package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida;

/**
 * Pergunta da turma gravada, à espera da resposta do personagem.
 *
 * @param perguntasRestantes quantas a turma ainda pode fazer; zero quando é hora de sugerir o quiz (RN-18)
 */
public record PerguntaRecebida(MensagemExibida pergunta, int perguntasRestantes) {}
