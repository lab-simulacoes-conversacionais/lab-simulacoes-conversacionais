package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Uma sinalização que aguarda a triagem, sem a pergunta, a resposta nem a justificativa: o bastante para o
 * resumo do prazo que o administrador também vê (RN-24, RN-37).
 */
public record PendenciaDaTriagem(long id, TipoDeErro tipo, Instant criadaEm) {

    public PendenciaDaTriagem {
        Objects.requireNonNull(tipo, "tipo");
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
