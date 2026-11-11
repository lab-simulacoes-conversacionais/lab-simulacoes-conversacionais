package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Aprovacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Devolucao;
import br.ufg.inf.labsimulacoes.eventos.CenarioAprovado;
import br.ufg.inf.labsimulacoes.eventos.CenarioDevolvido;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publica os eventos do módulo (Stack, seção 4), traduzindo o domínio para valores simples. O evento
 * sai dentro da transação de quem publicou e fica no registro de publicação do Spring Modulith; os
 * ouvintes, como o log de auditoria (RN-35), só o recebem depois da confirmação.
 */
@Component
public class EventosDeCenarios {

    private final ApplicationEventPublisher publicador;

    EventosDeCenarios(ApplicationEventPublisher publicador) {
        this.publicador = publicador;
    }

    /** @param cenario já aprovado e salvo */
    public void cenarioAprovado(Cenario cenario) {
        Aprovacao aprovacao = cenario.versao().aprovacao();
        publicador.publishEvent(
                new CenarioAprovado(cenario.id(), cenario.versao().id(), aprovacao.curadorId(), aprovacao.em()));
    }

    /** @param cenario já devolvido e salvo */
    public void cenarioDevolvido(Cenario cenario) {
        Devolucao devolucao = cenario.ultimaDevolucao();
        publicador.publishEvent(new CenarioDevolvido(
                cenario.id(), cenario.versao().id(), devolucao.curadorId(), devolucao.comentario(), devolucao.em()));
    }
}
