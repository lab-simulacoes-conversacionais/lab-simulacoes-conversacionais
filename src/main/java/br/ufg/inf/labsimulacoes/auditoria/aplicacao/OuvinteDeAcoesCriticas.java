package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import br.ufg.inf.labsimulacoes.eventos.PerfilAlterado;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Registra no log as ações críticas que os outros módulos publicam como eventos (RN-35). Cada
 * ouvinte roda depois da transação de quem publicou, em outra transação. Se a gravação falhar, o
 * evento continua pendente no registro de publicação e é reprocessado quando a aplicação reiniciar.
 */
@Component
class OuvinteDeAcoesCriticas {

    private final RegistroNoLog registro;

    OuvinteDeAcoesCriticas(RegistroNoLog registro) {
        this.registro = registro;
    }

    @ApplicationModuleListener
    void perfilAlterado(PerfilAlterado evento) {
        registro.registrar(RegistroDeAuditoria.novo(
                AcaoCritica.ALTERAR_PERFIS,
                evento.alteradoPorId(),
                String.valueOf(evento.usuarioId()),
                Map.of(
                        "perfisAnteriores", new TreeSet<>(evento.perfisAnteriores()),
                        "perfisNovos", new TreeSet<>(evento.perfisNovos())),
                evento.ocorridoEm()));
    }
}
