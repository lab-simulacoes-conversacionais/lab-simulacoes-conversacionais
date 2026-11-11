package br.ufg.inf.labsimulacoes.sessoes.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Pausa, retomada ou reinício, gravado em evento_sessao. Com eles o estado da sessão é reconstruído
 * e o tempo em pausa é calculado (RNF-04, RN-28).
 *
 * @param motivo obrigatório na pausa e ausente nos outros tipos
 * @param em quando aconteceu
 */
public record EventoDaSessao(Tipo tipo, MotivoDaPausa motivo, Instant em) {

    public enum Tipo {
        PAUSA,
        RETOMADA,
        /** Ponto de corte: a tela e o histórico enviado à IA passam a começar daqui (RN-19). */
        REINICIO
    }

    public EventoDaSessao {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(em, "em");
        if ((tipo == Tipo.PAUSA) != (motivo != null)) {
            throw new IllegalArgumentException("Só a pausa tem motivo, e ela sempre tem");
        }
    }

    public static EventoDaSessao pausa(MotivoDaPausa motivo, Instant em) {
        return new EventoDaSessao(Tipo.PAUSA, Objects.requireNonNull(motivo, "motivo"), em);
    }

    public static EventoDaSessao retomada(Instant em) {
        return new EventoDaSessao(Tipo.RETOMADA, null, em);
    }

    public static EventoDaSessao reinicio(Instant em) {
        return new EventoDaSessao(Tipo.REINICIO, null, em);
    }
}
