package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.GravidadeFinal;
import br.ufg.inf.labsimulacoes.auditoria.dominio.OrigemDaSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.PrazoDaTriagem;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoJaTriada;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.auditoria.dominio.Sinalizacoes;
import br.ufg.inf.labsimulacoes.auditoria.dominio.Triagem;
import br.ufg.inf.labsimulacoes.eventos.SinalizacaoTriada;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O curador confirma ou descarta uma sinalização pendente (RF-24). A confirmada leva a gravidade final,
 * Leve, Moderada ou Grave (RN-22); a descartada, não. Cada sinalização é triada uma vez só.
 *
 * <p>A triagem é gravada no log de ações críticas na mesma transação, com a decisão e se saiu fora do prazo
 * de 2 dias úteis (RN-24, RN-35), e publica {@link SinalizacaoTriada}, sem os textos. Até a RF-25, nada
 * reage ao evento: a triagem ainda não mantém nem retira a suspensão do cenário (RN-23b a d).
 */
@Service
@PreAuthorize("hasRole('CURADOR')")
public class TriagemDeSinalizacoes {

    public static final String METRICA = "lab.auditoria.triagem";

    private static final Logger LOG = LoggerFactory.getLogger(TriagemDeSinalizacoes.class);

    private final Sinalizacoes sinalizacoes;
    private final RegistroNoLog log;
    private final UsuarioLogado usuarioLogado;
    private final RelogioDaAuditoria relogio;
    private final ApplicationEventPublisher eventos;
    private final MeterRegistry metricas;
    private final ZoneId fuso;

    TriagemDeSinalizacoes(
            Sinalizacoes sinalizacoes,
            RegistroNoLog log,
            UsuarioLogado usuarioLogado,
            RelogioDaAuditoria relogio,
            ApplicationEventPublisher eventos,
            MeterRegistry metricas,
            @Value("${auditoria.triagem.fuso}") ZoneId fuso) {
        this.sinalizacoes = sinalizacoes;
        this.log = log;
        this.usuarioLogado = usuarioLogado;
        this.relogio = relogio;
        this.eventos = eventos;
        this.metricas = metricas;
        this.fuso = fuso;
    }

    /**
     * @throws IllegalArgumentException se faltar a gravidade final; nada é gravado
     * @throws SinalizacaoNaoEncontrada se a sinalização não existir
     * @throws SinalizacaoJaTriada se ela já foi confirmada ou descartada
     */
    @Transactional
    public void confirmar(long sinalizacaoId, GravidadeFinal gravidade) {
        triar(Triagem.confirmar(sinalizacaoId, gravidade, usuarioLogado.id(), relogio.agora()));
    }

    /**
     * @throws SinalizacaoNaoEncontrada se a sinalização não existir
     * @throws SinalizacaoJaTriada se ela já foi confirmada ou descartada
     */
    @Transactional
    public void descartar(long sinalizacaoId) {
        triar(Triagem.descartar(sinalizacaoId, usuarioLogado.id(), relogio.agora()));
    }

    private void triar(Triagem triagem) {
        long id = triagem.sinalizacaoId();
        OrigemDaSinalizacao origem = sinalizacoes.triar(triagem).orElseThrow(() -> sinalizacoes.existe(id)
                ? new SinalizacaoJaTriada(id)
                : new SinalizacaoNaoEncontrada(id));
        boolean foraDoPrazo = PrazoDaTriagem.foraDoPrazo(origem.criadaEm(), triagem.triadaEm(), fuso);
        String gravidadeFinal = triagem.confirmada() ? triagem.gravidade().name() : null;

        log.registrar(RegistroDeAuditoria.novo(
                AcaoCritica.TRIAR_SINALIZACAO,
                triagem.triadaPorId(),
                String.valueOf(id),
                detalhes(triagem, origem, foraDoPrazo),
                triagem.triadaEm()));
        eventos.publishEvent(new SinalizacaoTriada(
                id,
                origem.versaoId(),
                triagem.decisao().name(),
                gravidadeFinal,
                origem.gravidadePercebida().name(),
                origem.perfil().name(),
                triagem.triadaPorId(),
                triagem.triadaEm()));
        Counter.builder(METRICA)
                .description("Sinalizações triadas pelo curador (RF-24)")
                .tag("decisao", triagem.decisao().name().toLowerCase(Locale.ROOT))
                .tag("prazo", foraDoPrazo ? "fora" : "dentro")
                .register(metricas)
                .increment();
        LOG.info("Sinalização {} triada: {}{}", id, triagem.decisao(), foraDoPrazo ? " (fora do prazo)" : "");
    }

    /** O log não aceita valor nulo, então a gravidade final só entra na confirmada. */
    private static Map<String, Object> detalhes(Triagem triagem, OrigemDaSinalizacao origem, boolean foraDoPrazo) {
        var detalhes = new LinkedHashMap<String, Object>();
        detalhes.put("decisao", triagem.decisao().name());
        if (triagem.confirmada()) {
            detalhes.put("gravidadeFinal", triagem.gravidade().name());
        }
        detalhes.put("gravidadePercebida", origem.gravidadePercebida().name());
        detalhes.put("perfil", origem.perfil().name());
        detalhes.put("versaoId", origem.versaoId());
        detalhes.put("foraDoPrazo", foraDoPrazo);
        return detalhes;
    }
}
