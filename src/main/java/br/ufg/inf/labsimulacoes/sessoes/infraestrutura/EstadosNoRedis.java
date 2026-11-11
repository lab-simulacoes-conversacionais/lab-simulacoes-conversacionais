package br.ufg.inf.labsimulacoes.sessoes.infraestrutura;

import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Uma chave por sessão, {@code sessao:<id>:estado}, com o estado em JSON (Stack, seção 5). A validade
 * recomeça a cada gravação, então a chave some algumas horas depois que a sessão é encerrada ou fica
 * parada. O Redis não precisa de persistência em disco.
 *
 * <p>Falha do Redis não derruba a aula: a leitura devolve vazio e o estado é refeito a partir do
 * PostgreSQL; a gravação que falha apaga a chave, para que a próxima leitura também o refaça.
 */
@Component
class EstadosNoRedis implements EstadosDasSessoes {

    private static final Logger LOG = LoggerFactory.getLogger(EstadosNoRedis.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration validade;

    EstadosNoRedis(
            StringRedisTemplate redis, ObjectMapper json, @Value("${sessoes.estado.validade:6h}") Duration validade) {
        this.redis = redis;
        this.json = json;
        this.validade = validade;
    }

    static String chave(long sessaoId) {
        return "sessao:" + sessaoId + ":estado";
    }

    @Override
    public Optional<EstadoDaSessao> buscar(long sessaoId) {
        try {
            String valor = redis.opsForValue().get(chave(sessaoId));
            return valor == null ? Optional.empty() : Optional.of(json.readValue(valor, EstadoJson.class).paraDominio());
        } catch (JsonProcessingException | RuntimeException e) {
            LOG.warn("Estado da sessão {} ilegível no Redis; ele será refeito a partir do banco", sessaoId, e);
            return Optional.empty();
        }
    }

    @Override
    public void gravar(EstadoDaSessao estado) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    gravarAgora(estado);
                }
            });
        } else {
            gravarAgora(estado);
        }
    }

    private void gravarAgora(EstadoDaSessao estado) {
        String chave = chave(estado.sessaoId());
        try {
            redis.opsForValue().set(chave, json.writeValueAsString(EstadoJson.de(estado)), validade);
        } catch (JsonProcessingException | RuntimeException e) {
            LOG.warn("Não deu para gravar o estado da sessão {} no Redis", estado.sessaoId(), e);
            apagarSemFalhar(chave);
        }
    }

    /** Uma chave desatualizada mostraria a tela errada; sem ela, o estado é refeito do banco. */
    private void apagarSemFalhar(String chave) {
        try {
            redis.delete(chave);
        } catch (RuntimeException e) {
            LOG.warn("Não deu para apagar a chave {} do Redis", chave, e);
        }
    }

    /**
     * Formato gravado no Redis, separado do domínio para que renomear um campo do domínio não deixe
     * ilegíveis as chaves já gravadas: no pior caso, a leitura falha e o estado é refeito do banco.
     */
    record EstadoJson(
            long sessaoId,
            String status,
            String motivoDaPausa,
            Instant iniciadaEm,
            long tempoEmPausaNanos,
            Instant pausadaDesde,
            Instant reiniciadaEm,
            int perguntasDaTurma,
            List<MensagemJson> ultimasMensagens) {

        static EstadoJson de(EstadoDaSessao estado) {
            return new EstadoJson(
                    estado.sessaoId(),
                    estado.status().name(),
                    estado.motivoDaPausa() == null ? null : estado.motivoDaPausa().name(),
                    estado.iniciadaEm(),
                    estado.tempoEmPausa().toNanos(),
                    estado.pausadaDesde(),
                    estado.reiniciadaEm(),
                    estado.perguntasDaTurma(),
                    estado.ultimasMensagens().stream().map(MensagemJson::de).toList());
        }

        EstadoDaSessao paraDominio() {
            return new EstadoDaSessao(
                    sessaoId,
                    StatusDaSessao.valueOf(status),
                    motivoDaPausa == null ? null : MotivoDaPausa.valueOf(motivoDaPausa),
                    iniciadaEm,
                    Duration.ofNanos(tempoEmPausaNanos),
                    pausadaDesde,
                    reiniciadaEm,
                    perguntasDaTurma,
                    ultimasMensagens == null ? List.of() : ultimasMensagens.stream().map(MensagemJson::paraDominio).toList());
        }
    }

    record MensagemJson(String autoria, String texto, Instant em) {

        static MensagemJson de(MensagemExibida mensagem) {
            return new MensagemJson(mensagem.autoria().name(), mensagem.texto(), mensagem.em());
        }

        MensagemExibida paraDominio() {
            return new MensagemExibida(MensagemExibida.Autoria.valueOf(autoria), texto, em);
        }
    }
}
