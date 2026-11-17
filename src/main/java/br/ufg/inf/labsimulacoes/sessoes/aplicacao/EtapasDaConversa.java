package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Mede as etapas de uma pergunta e da resposta do personagem do lado da sessão, para separar onde vai o
 * tempo até a primeira frase aprovada na tela (RNF-03, issue #80). Cada etapa vira uma observação
 * {@value #OBSERVACAO}, com o nome da etapa na chave {@value #CHAVE}; a moderação e a geração são medidas
 * pelo adaptador de IA.
 */
@Component
public class EtapasDaConversa {

    public static final String OBSERVACAO = "lab.conversa.etapa";
    public static final String CHAVE = "etapa";

    /** As etapas medidas. O valor é o que aparece na chave da observação. */
    public enum Etapa {
        /** Leitura da sessão e do estado no banco, antes de moderar a pergunta. */
        CONFERENCIA_DA_SESSAO("conferencia-da-sessao"),
        /** Transação que grava a pergunta no banco, incluindo o Redis gravado logo depois da confirmação. */
        GRAVACAO_DA_PERGUNTA("gravacao-da-pergunta"),
        /** Do canal aberto até o pedido ir ao adaptador: sessão, estado e cenário lidos do banco. */
        PREPARO_DA_RESPOSTA("preparo-da-resposta"),
        /** Juntar um trecho do modelo às frases que ele fecha. */
        AGRUPAMENTO_DE_FRASES("agrupamento-de-frases"),
        /** Transação que grava a resposta exibida, incluindo o Redis. */
        GRAVACAO_DA_RESPOSTA("gravacao-da-resposta"),
        /** Do pedido do canal até a thread da resposta começar. */
        ESPERA_DA_THREAD("espera-da-thread"),
        /** Envio de uma frase aprovada pelo canal SSE. */
        ENVIO_SSE_TRECHO("envio-sse-trecho"),
        /** Envio do aviso de bloqueio ou de falha pelo canal SSE. */
        ENVIO_SSE_AVISO("envio-sse-aviso"),
        /** Envio do evento que fecha o canal SSE. */
        ENVIO_SSE_FIM("envio-sse-fim");

        private final String valor;

        Etapa(String valor) {
            this.valor = valor;
        }

        public String valor() {
            return valor;
        }
    }

    private final ObservationRegistry registro;

    public EtapasDaConversa(ObservationRegistry registro) {
        this.registro = registro;
    }

    /** Mede a etapa enquanto a ação roda; uma exceção da ação fica registrada e sai sem mudança. */
    public <T> T medir(Etapa etapa, Supplier<T> acao) {
        return observacao(etapa).observe(acao);
    }

    public void medir(Etapa etapa, Runnable acao) {
        observacao(etapa).observe(acao);
    }

    /**
     * Para a etapa que não cabe num bloco: começa numa thread e termina em outra, ou termina no meio de
     * um método. Quem chama precisa pará-la.
     */
    public EtapaEmAndamento iniciar(Etapa etapa) {
        return new EtapaEmAndamento(observacao(etapa).start());
    }

    /** Uma etapa iniciada. Só a primeira parada vale, para a etapa não ser medida duas vezes. */
    public static final class EtapaEmAndamento {

        private final Observation observacao;
        private final AtomicBoolean parada = new AtomicBoolean();

        private EtapaEmAndamento(Observation observacao) {
            this.observacao = observacao;
        }

        public void parar() {
            if (parada.compareAndSet(false, true)) {
                observacao.stop();
            }
        }

        /** Para a etapa com o erro que a interrompeu. */
        public void falhar(Throwable erro) {
            if (parada.compareAndSet(false, true)) {
                observacao.error(erro);
                observacao.stop();
            }
        }
    }

    private Observation observacao(Etapa etapa) {
        return Observation.createNotStarted(OBSERVACAO, registro).lowCardinalityKeyValue(CHAVE, etapa.valor());
    }
}
