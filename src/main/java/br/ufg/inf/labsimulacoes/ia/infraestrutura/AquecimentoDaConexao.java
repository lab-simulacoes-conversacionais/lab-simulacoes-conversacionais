package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Abre a conexão com o provedor de IA assim que a aplicação sobe, para a primeira pergunta da aula não
 * pagar o DNS, o handshake TCP e TLS e a carga das classes do cliente (RNF-03, issue #80).
 *
 * <p>Usa uma moderação, que não gasta tokens. Como a moderação e o streaming dividem a conexão
 * ({@link ConexaoComOProvedor}), ela aquece as duas. Roda numa thread à parte, para não atrasar a subida,
 * e uma falha só fica no log: o provedor fora do ar não impede a aplicação de subir.
 */
@Component
@ConditionalOnProperty(name = "ia.aquecimento.habilitado", havingValue = "true")
class AquecimentoDaConexao {

    private static final Logger LOG = LoggerFactory.getLogger(AquecimentoDaConexao.class);
    static final String TEXTO = "Olá, turma.";

    private final ProvedorDeIa provedor;

    AquecimentoDaConexao(ProvedorDeIa provedor) {
        this.provedor = provedor;
    }

    @EventListener(ApplicationReadyEvent.class)
    void aoSubir() {
        Thread.ofVirtual().name("aquecimento-do-provedor-de-ia").start(this::aquecer);
    }

    /** A mensagem de outro erro pode trazer o endereço do provedor, por isso só o tipo vai para o log. */
    void aquecer() {
        long inicio = System.nanoTime();
        try {
            provedor.moderar(TEXTO);
            LOG.info("Conexão com o provedor de IA aberta em {} ms", (System.nanoTime() - inicio) / 1_000_000);
        } catch (FalhaNoProvedorDeIa e) {
            LOG.warn("Não deu para abrir a conexão com o provedor de IA ao subir: {}", e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("Não deu para abrir a conexão com o provedor de IA ao subir: {}", e.getClass().getSimpleName());
        }
    }
}
