package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Uma resposta da IA sinalizada por um professor ou um curador como possível erro (RF-23, RN-21). Exige
 * tipo, gravidade percebida e uma justificativa curta. Toda sinalização entra pendente, e o curador a
 * confirma ou descarta na triagem ({@link Triagem}, RF-24).
 *
 * @param justificativa sem os espaços das pontas; de 1 a {@value #TAMANHO_MAXIMO_DA_JUSTIFICATIVA}
 *     caracteres
 */
public record Sinalizacao(
        TrechoSinalizado trecho,
        TipoDeErro tipo,
        GravidadePercebida gravidade,
        String justificativa,
        long sinalizadoPorId,
        PerfilDaSinalizacao perfil,
        Instant criadaEm) {

    /** "Justificativa curta" (RN-21). Fica na aplicação, e não no banco, para mudar sem migration. */
    public static final int TAMANHO_MAXIMO_DA_JUSTIFICATIVA = 500;

    /** Toda sinalização entra pendente; os outros status são os da {@link DecisaoDaTriagem} (RF-24). */
    public static final String PENDENTE = "PENDENTE";

    public Sinalizacao {
        Objects.requireNonNull(trecho, "trecho");
        Objects.requireNonNull(perfil, "perfil");
        Objects.requireNonNull(criadaEm, "criadaEm");
        if (tipo == null) {
            throw new IllegalArgumentException("Escolha o tipo do erro");
        }
        if (gravidade == null) {
            throw new IllegalArgumentException("Escolha a gravidade percebida");
        }
        justificativa = justificativa == null ? "" : justificativa.strip();
        if (justificativa.isEmpty()) {
            throw new IllegalArgumentException("Escreva a justificativa");
        }
        if (justificativa.length() > TAMANHO_MAXIMO_DA_JUSTIFICATIVA) {
            throw new IllegalArgumentException(
                    "A justificativa tem no máximo " + TAMANHO_MAXIMO_DA_JUSTIFICATIVA + " caracteres");
        }
    }
}
