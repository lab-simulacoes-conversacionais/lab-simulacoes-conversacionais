package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Hora atual da Auditoria, para o prazo da sinalização (RN-21) poder ser testado com um momento fixo.
 * O de Sessões é interno àquele módulo, e o projeto não tem um relógio compartilhado.
 */
@Component
class RelogioDaAuditoria {

    /** O PostgreSQL guarda microssegundos; assim o momento gravado é igual ao do evento. */
    Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
