package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;
import java.util.Map;

/**
 * Uma linha do log de ações críticas (RN-35). Não tem métodos de alteração: depois de criada, só
 * pode ser gravada.
 *
 * @param id nulo enquanto não foi gravada
 * @param usuarioId quem fez a ação
 * @param acao o que foi feito
 * @param entidadeId identificador do que foi afetado; nulo quando a ação não afeta um registro só
 * @param detalhes informações da ação que ajudam a entendê-la depois
 * @param ocorridoEm quando a ação aconteceu, não quando foi gravada
 */
public record RegistroDeAuditoria(
        Long id, long usuarioId, AcaoCritica acao, String entidadeId, Map<String, Object> detalhes, Instant ocorridoEm) {

    public RegistroDeAuditoria {
        if (usuarioId <= 0) {
            throw new IllegalArgumentException("Toda ação crítica precisa do usuário que a fez (RN-35)");
        }
        if (acao == null) {
            throw new IllegalArgumentException("A ação é obrigatória");
        }
        if (ocorridoEm == null) {
            throw new IllegalArgumentException("Toda ação crítica precisa da data e hora em que ocorreu (RN-35)");
        }
        detalhes = detalhes == null ? Map.of() : Map.copyOf(detalhes);
    }

    public static RegistroDeAuditoria novo(
            AcaoCritica acao, long usuarioId, String entidadeId, Map<String, ?> detalhes, Instant ocorridoEm) {
        return new RegistroDeAuditoria(
                null, usuarioId, acao, entidadeId, detalhes == null ? null : Map.<String, Object>copyOf(detalhes),
                ocorridoEm);
    }

    public String entidade() {
        return acao.entidade();
    }
}
