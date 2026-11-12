package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import br.ufg.inf.labsimulacoes.auditoria.dominio.CasoBloqueado;
import br.ufg.inf.labsimulacoes.auditoria.dominio.CasosBloqueados;
import br.ufg.inf.labsimulacoes.eventos.ConteudoBloqueado;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Abre o caso na fila do curador quando Sessões bloqueia um conteúdo (RN-20, Stack, seção 4). Roda
 * depois da transação de quem publicou, em outra transação; se a gravação falhar, o evento continua
 * pendente no registro de publicação e é reprocessado quando a aplicação reiniciar.
 */
@Component
class OuvinteDeConteudoBloqueado {

    private final CasosBloqueados casos;

    OuvinteDeConteudoBloqueado(CasosBloqueados casos) {
        this.casos = casos;
    }

    @ApplicationModuleListener
    void conteudoBloqueado(ConteudoBloqueado evento) {
        casos.abrir(new CasoBloqueado(
                evento.sessaoId(),
                evento.versaoId(),
                CasoBloqueado.Origem.valueOf(evento.origem().name()),
                evento.pergunta(),
                evento.trechoBloqueado(),
                evento.categorias(),
                evento.respostaIaMetaId(),
                evento.ocorridoEm()));
    }
}
