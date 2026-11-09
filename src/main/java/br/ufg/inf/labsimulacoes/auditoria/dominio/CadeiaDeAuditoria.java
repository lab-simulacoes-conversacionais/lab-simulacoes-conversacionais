package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Conferência do hash encadeado do log. Detecta linha alterada, apagada no meio ou inserida sem
 * passar pelo sistema. Apagar só a última linha não deixa rastro na cadeia; contra isso vale o
 * trigger do banco.
 */
public final class CadeiaDeAuditoria {

    private CadeiaDeAuditoria() {}

    /** Recebe as linhas na ordem em que foram gravadas e devolve a primeira que não confere. */
    public static Optional<RegistroDeAuditoria> primeiraAdulterada(List<RegistroDeAuditoria> emOrdem) {
        String anterior = null;
        for (RegistroDeAuditoria registro : emOrdem) {
            if (!registro.confereCom(anterior)) {
                return Optional.of(registro);
            }
            anterior = registro.hash();
        }
        return Optional.empty();
    }
}
