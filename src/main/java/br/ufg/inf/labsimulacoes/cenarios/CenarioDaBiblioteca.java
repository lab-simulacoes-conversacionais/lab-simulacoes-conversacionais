package br.ufg.inf.labsimulacoes.cenarios;

import java.time.Instant;
import java.util.List;

/**
 * Resumo de um cenário da biblioteca, com o que a lista mostra e o que a E2 vai filtrar (componente,
 * ano e habilidade BNCC). O roteiro completo vem de {@link CenariosAprovados#versaoVigente(long)}.
 *
 * @param cenarioId cenário, para pedir a versão vigente ao iniciar a sessão
 * @param versaoId versão vigente no momento da consulta
 * @param numeroDaVersao número dessa versão no cenário
 * @param habilidades códigos BNCC vinculados (RN-02)
 * @param aprovadoEm momento em que a versão vigente foi aprovada
 */
public record CenarioDaBiblioteca(
        long cenarioId,
        long versaoId,
        int numeroDaVersao,
        String titulo,
        String componente,
        String etapa,
        Integer ano,
        List<String> habilidades,
        String faixaEtaria,
        Integer duracaoPrevistaMin,
        Instant aprovadoEm) {

    public CenarioDaBiblioteca {
        habilidades = List.copyOf(habilidades);
    }
}
