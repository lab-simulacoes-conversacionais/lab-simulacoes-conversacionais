package br.ufg.inf.labsimulacoes.sessoes.dominio;

import java.util.List;
import java.util.Objects;

/**
 * O que o PostgreSQL guarda de uma sessão, para reconstruir o estado quando a chave do Redis some
 * (RNF-04, Stack, seção 5).
 *
 * @param eventos pausas, retomadas e reinícios, em ordem
 * @param perguntasDaTurma todas as perguntas da sessão, inclusive as de antes de um reinício
 * @param mensagensDesdeOReinicio as mais recentes depois do último reinício, em ordem, até
 *     {@link EstadoDaSessao#MENSAGENS_GUARDADAS}
 */
public record HistoricoDaSessao(
        Sessao sessao, List<EventoDaSessao> eventos, int perguntasDaTurma, List<MensagemExibida> mensagensDesdeOReinicio) {

    public HistoricoDaSessao {
        Objects.requireNonNull(sessao, "sessao");
        eventos = List.copyOf(eventos);
        mensagensDesdeOReinicio = List.copyOf(mensagensDesdeOReinicio);
    }
}
