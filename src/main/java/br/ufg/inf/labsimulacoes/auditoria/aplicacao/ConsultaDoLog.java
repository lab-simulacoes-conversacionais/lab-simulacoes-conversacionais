package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import br.ufg.inf.labsimulacoes.auditoria.dominio.FiltroDoLog;
import br.ufg.inf.labsimulacoes.auditoria.dominio.LogDeAuditoria;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta do log de ações críticas pelo curador e pelo administrador (RF-26). Só lê: não há
 * operação de alterar nem de apagar, a transação é somente leitura e o banco recusa UPDATE e DELETE
 * na tabela (RN-35).
 */
@Service
@PreAuthorize("hasAnyRole('CURADOR', 'ADMINISTRADOR')")
public class ConsultaDoLog {

    private final LogDeAuditoria log;

    ConsultaDoLog(LogDeAuditoria log) {
        this.log = log;
    }

    /** Sem filtro, traz a primeira página do log inteiro. */
    @Transactional(readOnly = true)
    public List<RegistroDeAuditoria> consultar(FiltroDoLog filtro) {
        return log.consultar(filtro == null ? FiltroDoLog.tudo() : filtro);
    }
}
