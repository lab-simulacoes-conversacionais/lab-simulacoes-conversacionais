package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/** Hora atual dos controles da sessão. */
@Component
class Relogio {

    /** O PostgreSQL guarda microssegundos; assim o momento lido do banco é igual ao do Redis. */
    Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
