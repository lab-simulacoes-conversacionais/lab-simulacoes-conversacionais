package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * O que a triagem devolve da sinalização triada, para o log e o evento: a versão do cenário, o que quem
 * sinalizou escolheu e quando sinalizou.
 */
public record OrigemDaSinalizacao(
        long versaoId, GravidadePercebida gravidadePercebida, PerfilDaSinalizacao perfil, Instant criadaEm) {

    public OrigemDaSinalizacao {
        Objects.requireNonNull(gravidadePercebida, "gravidadePercebida");
        Objects.requireNonNull(perfil, "perfil");
        Objects.requireNonNull(criadaEm, "criadaEm");
    }
}
