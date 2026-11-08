package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import br.ufg.inf.labsimulacoes.auditoria.dominio.LogDeAuditoria;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava uma ação crítica no log, encadeada na última linha (RN-35). Não é um caso de uso: quem chama
 * são os ouvintes de eventos e os casos de uso da própria Auditoria, que já verificaram o perfil.
 */
@Component
class RegistroNoLog {

    private final LogDeAuditoria log;

    RegistroNoLog(LogDeAuditoria log) {
        this.log = log;
    }

    @Transactional
    RegistroDeAuditoria registrar(RegistroDeAuditoria registro) {
        String anterior = log.travarEBuscarUltimoHash().orElse(null);
        return log.gravar(registro.encadeadoApos(anterior));
    }
}
