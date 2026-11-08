package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import br.ufg.inf.labsimulacoes.eventos.CenarioAprovado;
import br.ufg.inf.labsimulacoes.eventos.CenarioArquivado;
import br.ufg.inf.labsimulacoes.eventos.CenarioDevolvido;
import br.ufg.inf.labsimulacoes.eventos.CenarioReativado;
import br.ufg.inf.labsimulacoes.eventos.CenarioSuspenso;
import br.ufg.inf.labsimulacoes.eventos.PerfilAlterado;
import br.ufg.inf.labsimulacoes.eventos.RelatorioExportado;
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

    @ApplicationModuleListener
    void cenarioAprovado(CenarioAprovado evento) {
        registro.registrar(RegistroDeAuditoria.novo(
                AcaoCritica.APROVAR_CENARIO,
                evento.curadorId(),
                String.valueOf(evento.cenarioId()),
                Map.of("versaoId", evento.versaoId()),
                evento.ocorridoEm()));
    }

    @ApplicationModuleListener
    void cenarioDevolvido(CenarioDevolvido evento) {
        registro.registrar(RegistroDeAuditoria.novo(
                AcaoCritica.DEVOLVER_CENARIO,
                evento.curadorId(),
                String.valueOf(evento.cenarioId()),
                Map.of("versaoId", evento.versaoId(), "comentario", evento.comentario()),
                evento.ocorridoEm()));
    }

    @ApplicationModuleListener
    void cenarioArquivado(CenarioArquivado evento) {
        registro.registrar(RegistroDeAuditoria.novo(
                AcaoCritica.ARQUIVAR_CENARIO,
                evento.arquivadoPorId(),
                String.valueOf(evento.cenarioId()),
                Map.of(),
                evento.ocorridoEm()));
    }

    @ApplicationModuleListener
    void cenarioSuspenso(CenarioSuspenso evento) {
        registro.registrar(RegistroDeAuditoria.novo(
                AcaoCritica.SUSPENDER_CENARIO,
                evento.responsavelId(),
                String.valueOf(evento.cenarioId()),
                Map.of("versaoId", evento.versaoId(), "motivo", evento.motivo()),
                evento.ocorridoEm()));
    }

    @ApplicationModuleListener
    void cenarioReativado(CenarioReativado evento) {
        registro.registrar(RegistroDeAuditoria.novo(
                AcaoCritica.REATIVAR_CENARIO,
                evento.responsavelId(),
                String.valueOf(evento.cenarioId()),
                Map.of("versaoId", evento.versaoId(), "motivo", evento.motivo()),
                evento.ocorridoEm()));
    }

    /** O relatório não é um registro do banco, então a linha não tem entidade_id. */
    @ApplicationModuleListener
    void relatorioExportado(RelatorioExportado evento) {
        registro.registrar(RegistroDeAuditoria.novo(
                AcaoCritica.EXPORTAR_RELATORIO,
                evento.usuarioId(),
                null,
                Map.of("formato", evento.formato(), "filtros", evento.filtros()),
                evento.ocorridoEm()));
    }
}
