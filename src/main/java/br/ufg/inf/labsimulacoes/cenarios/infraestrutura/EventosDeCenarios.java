package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Aprovacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Devolucao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.MotivoDaReativacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.MotivoDaSuspensao;
import br.ufg.inf.labsimulacoes.eventos.CenarioAprovado;
import br.ufg.inf.labsimulacoes.eventos.CenarioArquivado;
import br.ufg.inf.labsimulacoes.eventos.CenarioDevolvido;
import br.ufg.inf.labsimulacoes.eventos.CenarioReativado;
import br.ufg.inf.labsimulacoes.eventos.CenarioSuspenso;
import java.time.Instant;
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

    /**
     * @param cenario já arquivado e salvo; a versão dele é a que era a vigente
     * @param curadorId quem arquivou
     * @param momento quando arquivou
     */
    public void cenarioArquivado(Cenario cenario, long curadorId, Instant momento) {
        publicador.publishEvent(new CenarioArquivado(cenario.id(), cenario.versao().id(), curadorId, momento));
    }

    /**
     * RN-23: o cenário saiu da sala pelas regras de gravidade.
     *
     * @param cenario já suspenso e salvo
     * @param versaoId a versão vigente, em que o erro foi sinalizado
     * @param responsavelId quem sinalizou (RN-23a) ou o curador que triou (RN-23b, RN-23c)
     * @param momento o da sinalização ou da triagem que suspendeu
     */
    public void cenarioSuspenso(
            Cenario cenario, long versaoId, long responsavelId, MotivoDaSuspensao motivo, Instant momento) {
        publicador.publishEvent(new CenarioSuspenso(cenario.id(), versaoId, responsavelId, motivo.name(), momento));
    }

    /**
     * O cenário suspenso voltou à sala (RN-23b, RN-23d).
     *
     * @param cenario já reativado e salvo
     * @param versaoId a versão que volta a valer em sala
     * @param responsavelId o curador cuja decisão reativou o cenário
     * @param momento o da triagem ou da aprovação que reativou
     */
    public void cenarioReativado(
            Cenario cenario, long versaoId, long responsavelId, MotivoDaReativacao motivo, Instant momento) {
        publicador.publishEvent(new CenarioReativado(cenario.id(), versaoId, responsavelId, motivo.name(), momento));
    }

    /** @param cenario já devolvido e salvo */
    public void cenarioDevolvido(Cenario cenario) {
        Devolucao devolucao = cenario.ultimaDevolucao();
        publicador.publishEvent(new CenarioDevolvido(
                cenario.id(), cenario.versao().id(), devolucao.curadorId(), devolucao.comentario(), devolucao.em()));
    }
}
