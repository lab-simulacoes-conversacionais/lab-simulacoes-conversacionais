package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * A sinalização que o curador vai triar (RF-24), lida antes da triagem com o que o log e o evento precisam:
 * a versão do cenário, o que quem sinalizou escolheu e quando sinalizou.
 *
 * @param pendente se ainda aguarda a triagem; cada sinalização é triada uma vez só
 */
public record SinalizacaoATriar(
        long id,
        boolean pendente,
        long versaoId,
        GravidadePercebida gravidadePercebida,
        PerfilDaSinalizacao perfil,
        Instant criadaEm) {

    public SinalizacaoATriar {
        Objects.requireNonNull(gravidadePercebida, "gravidadePercebida");
        Objects.requireNonNull(perfil, "perfil");
        Objects.requireNonNull(criadaEm, "criadaEm");
    }

    /**
     * Confere se a decisão do curador pode ser aplicada a esta sinalização (RF-24).
     *
     * @throws SinalizacaoJaTriada se ela já foi confirmada ou descartada
     * @throws IllegalArgumentException se a triagem é de outra sinalização
     */
    public void aceitar(Triagem triagem) {
        if (triagem.sinalizacaoId() != id) {
            throw new IllegalArgumentException(
                    "A triagem da sinalização " + triagem.sinalizacaoId() + " não é da sinalização " + id);
        }
        if (!pendente) {
            throw new SinalizacaoJaTriada(id);
        }
    }

    /** Se a triagem feita em {@code triadaEm} saiu depois dos 2 dias úteis (RN-24). */
    public boolean foraDoPrazo(Instant triadaEm, ZoneId fuso) {
        return PrazoDaTriagem.foraDoPrazo(criadaEm, triadaEm, fuso);
    }
}
