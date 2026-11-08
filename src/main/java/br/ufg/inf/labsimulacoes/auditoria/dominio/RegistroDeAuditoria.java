package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

/**
 * Uma linha do log de ações críticas (RN-35). Não tem métodos de alteração: depois de criada, só
 * pode ser gravada.
 *
 * <p>Cada linha guarda o hash do seu conteúdo junto com o hash da linha anterior. Quem alterar ou
 * apagar uma linha direto no banco, contornando o trigger, quebra a cadeia a partir dali (Stack,
 * seção 6).
 *
 * @param id nulo enquanto não foi gravada
 * @param usuarioId quem fez a ação
 * @param acao o que foi feito
 * @param entidadeId identificador do que foi afetado; nulo quando a ação não afeta um registro só
 * @param detalhes informações da ação que ajudam a entendê-la depois
 * @param ocorridoEm quando a ação aconteceu, não quando foi gravada; fica em microssegundos, a
 *     precisão do PostgreSQL, para o hash ser o mesmo depois de gravado
 * @param hash nulo enquanto a linha não foi encadeada
 */
public record RegistroDeAuditoria(
        Long id,
        long usuarioId,
        AcaoCritica acao,
        String entidadeId,
        Map<String, Object> detalhes,
        Instant ocorridoEm,
        String hash) {

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
        detalhes = FormaCanonica.normalizarDetalhes(detalhes);
        ocorridoEm = ocorridoEm.truncatedTo(ChronoUnit.MICROS);
    }

    public static RegistroDeAuditoria novo(
            AcaoCritica acao, long usuarioId, String entidadeId, Map<String, ?> detalhes, Instant ocorridoEm) {
        return new RegistroDeAuditoria(
                null, usuarioId, acao, entidadeId, FormaCanonica.normalizarDetalhes(detalhes), ocorridoEm, null);
    }

    public String entidade() {
        return acao.entidade();
    }

    /** A mesma linha com o hash calculado a partir da última linha do log, que é nula no log vazio. */
    public RegistroDeAuditoria encadeadoApos(String hashAnterior) {
        return new RegistroDeAuditoria(
                id, usuarioId, acao, entidadeId, detalhes, ocorridoEm, calcularHash(hashAnterior));
    }

    /** Se o conteúdo desta linha e o hash da linha anterior ainda dão o hash guardado. */
    public boolean confereCom(String hashAnterior) {
        return hash != null && hash.equals(calcularHash(hashAnterior));
    }

    private String calcularHash(String hashAnterior) {
        String conteudo = FormaCanonica.de(Arrays.asList(
                hashAnterior, usuarioId, acao.name(), entidade(), entidadeId, detalhes, ocorridoEm.toString()));
        return HexFormat.of().formatHex(sha256().digest(conteudo.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Toda JVM precisa ter SHA-256", e);
        }
    }
}
