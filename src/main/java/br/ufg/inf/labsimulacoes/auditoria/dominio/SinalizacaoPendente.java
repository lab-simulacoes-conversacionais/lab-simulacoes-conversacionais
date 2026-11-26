package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Uma sinalização que aguarda a triagem do curador (RF-24), com o que ele precisa para decidir: a pergunta
 * e a resposta copiadas, o que quem sinalizou escolheu e a justificativa.
 *
 * @param sessaoId a sessão; nula só depois da retenção (RN-33)
 */
public record SinalizacaoPendente(
        long id,
        Long sessaoId,
        long versaoId,
        String pergunta,
        String resposta,
        TipoDeErro tipo,
        GravidadePercebida gravidadePercebida,
        String justificativa,
        PerfilDaSinalizacao perfil,
        long sinalizadoPorId,
        Instant criadaEm) {

    public SinalizacaoPendente {
        Objects.requireNonNull(pergunta, "pergunta");
        Objects.requireNonNull(resposta, "resposta");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(gravidadePercebida, "gravidadePercebida");
        Objects.requireNonNull(justificativa, "justificativa");
        Objects.requireNonNull(perfil, "perfil");
        Objects.requireNonNull(criadaEm, "criadaEm");
    }

    /** Até quando o curador tem para triar (RN-24). */
    public Instant limiteDaTriagem(ZoneId fuso) {
        return PrazoDaTriagem.limite(criadaEm, fuso);
    }

    /** Se passou dos 2 dias úteis e deve ficar em destaque para o curador e o administrador (RN-24). */
    public boolean foraDoPrazo(Instant agora, ZoneId fuso) {
        return PrazoDaTriagem.foraDoPrazo(criadaEm, agora, fuso);
    }
}
