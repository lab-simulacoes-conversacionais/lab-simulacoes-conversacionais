package br.ufg.inf.labsimulacoes.cenarios;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot imutável de uma versão aprovada (RN-08), com o que a sessão precisa para conduzir a
 * conversa. A sessão guarda o {@code versaoId}, nunca só o cenário (Stack, seção 6).
 *
 * @param cenarioId cenário a que a versão pertence
 * @param versaoId versão vigente, que a sessão referencia
 * @param numero número da versão no cenário
 * @param habilidades códigos BNCC vinculados (RN-02)
 * @param aprovadaEm momento da aprovação
 */
public record VersaoAprovada(
        long cenarioId,
        long versaoId,
        int numero,
        String titulo,
        String personagem,
        String papelDaIa,
        String contexto,
        List<String> objetivos,
        List<String> limitesDeAssunto,
        List<String> fontes,
        List<String> perguntasDisparadoras,
        String componente,
        String etapa,
        Integer ano,
        List<String> habilidades,
        String faixaEtaria,
        Integer duracaoPrevistaMin,
        Instant aprovadaEm) {

    public VersaoAprovada {
        objetivos = List.copyOf(objetivos);
        limitesDeAssunto = List.copyOf(limitesDeAssunto);
        fontes = List.copyOf(fontes);
        perguntasDisparadoras = List.copyOf(perguntasDisparadoras);
        habilidades = List.copyOf(habilidades);
    }
}
