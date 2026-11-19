package br.ufg.inf.labsimulacoes.ia.aplicacao;

import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.PedidoDeSugestao;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.ia.RoteiroSugerido;
import br.ufg.inf.labsimulacoes.ia.SugestaoDeCenario;
import br.ufg.inf.labsimulacoes.ia.dominio.Geracao;
import br.ufg.inf.labsimulacoes.ia.dominio.MetadadosDaResposta;
import br.ufg.inf.labsimulacoes.ia.dominio.MontadorDePrompt;
import br.ufg.inf.labsimulacoes.ia.dominio.PromptMontado;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.dominio.RegistroDeMetadados;
import br.ufg.inf.labsimulacoes.ia.dominio.TabelaDePrecos;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Porta do adaptador de IA: monta o prompt, chama o provedor e grava os metadados de cada resposta
 * gerada (RNF-10, RNF-13).
 *
 * <p>Não abre transação: a resposta em streaming pode levar segundos, e a gravação dos metadados é uma
 * linha só. A latência conta do pedido ao provedor até o fim da resposta.
 *
 * <p>Cada etapa vira uma observação {@value #OBSERVACAO}, com a etapa na chave {@value #CHAVE}, para
 * separar onde vai o tempo até a primeira frase na tela (RNF-03): a montagem do prompt, a espera até o
 * primeiro trecho do modelo, a geração inteira e cada moderação.
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
public class ChamadasAoProvedor implements AdaptadorDeIa {

    public static final String OBSERVACAO = "lab.ia.chamada";
    public static final String CHAVE = "etapa";

    static final String MONTAGEM_DO_PROMPT = "montagem-do-prompt";
    static final String ESPERA_DO_PRIMEIRO_TRECHO = "espera-do-primeiro-trecho";
    static final String GERACAO = "geracao";
    static final String MODERACAO = "moderacao";

    private final ProvedorDeIa provedor;
    private final MontadorDePrompt montador;
    private final RegistroDeMetadados registro;
    private final TabelaDePrecos precos;
    private final ObservationRegistry observacoes;

    ChamadasAoProvedor(
            ProvedorDeIa provedor,
            MontadorDePrompt montador,
            RegistroDeMetadados registro,
            TabelaDePrecos precos,
            ObservationRegistry observacoes) {
        this.provedor = provedor;
        this.montador = montador;
        this.registro = registro;
        this.precos = precos;
        this.observacoes = observacoes;
    }

    @Override
    public RespostaDoPersonagem responder(PedidoDeFala pedido, Consumer<String> aoReceberTrecho) {
        if (pedido == null || aoReceberTrecho == null) {
            throw new IllegalArgumentException("O pedido e quem recebe os trechos são obrigatórios");
        }
        PromptMontado prompt = medir(MONTAGEM_DO_PROMPT, () -> montador.daFala(pedido));
        long inicio = System.nanoTime();
        Geracao<String> geracao = gerar(prompt, aoReceberTrecho);
        long metadadosId = registrar(prompt, geracao, inicio).id();
        if (geracao.conteudo() == null || geracao.conteudo().isBlank()) {
            throw new FalhaNoProvedorDeIa("O provedor de IA devolveu uma resposta vazia", null);
        }
        return new RespostaDoPersonagem(geracao.conteudo(), metadadosId);
    }

    @Override
    public SugestaoDeCenario sugerirCenario(PedidoDeSugestao pedido) {
        if (pedido == null) {
            throw new IllegalArgumentException("O pedido de sugestão é obrigatório");
        }
        PromptMontado prompt = montador.daSugestao(pedido);
        long inicio = System.nanoTime();
        Geracao<RoteiroSugerido> geracao = provedor.sugerirRoteiro(prompt);
        long metadadosId = registrar(prompt, geracao, inicio).id();
        if (geracao.conteudo() == null) {
            throw new FalhaNoProvedorDeIa("O provedor de IA não devolveu um roteiro", null);
        }
        return new SugestaoDeCenario(geracao.conteudo(), metadadosId);
    }

    @Override
    public ResultadoDaModeracao moderar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("O texto a moderar é obrigatório");
        }
        return medir(MODERACAO, () -> provedor.moderar(texto));
    }

    /**
     * A espera pelo primeiro trecho para no primeiro trecho, antes de ele seguir para quem chamou. Se o
     * provedor falhar ou terminar sem nenhum trecho, ela para com o erro, para não contar como uma espera
     * que deu certo.
     */
    private Geracao<String> gerar(PromptMontado prompt, Consumer<String> aoReceberTrecho) {
        Observation espera = observacao(ESPERA_DO_PRIMEIRO_TRECHO).start();
        boolean[] esperando = {true};
        try {
            Geracao<String> geracao = medir(GERACAO, () -> provedor.conversar(prompt, trecho -> {
                if (esperando[0]) {
                    esperando[0] = false;
                    espera.stop();
                }
                aoReceberTrecho.accept(trecho);
            }));
            if (esperando[0]) {
                espera.error(new FalhaNoProvedorDeIa("O provedor terminou sem nenhum trecho", null));
            }
            return geracao;
        } catch (RuntimeException e) {
            if (esperando[0]) {
                espera.error(e);
            }
            throw e;
        } finally {
            if (esperando[0]) {
                espera.stop();
            }
        }
    }

    private <T> T medir(String etapa, Supplier<T> chamada) {
        return observacao(etapa).observe(chamada);
    }

    private Observation observacao(String etapa) {
        return Observation.createNotStarted(OBSERVACAO, observacoes).lowCardinalityKeyValue(CHAVE, etapa);
    }

    private MetadadosDaResposta registrar(PromptMontado prompt, Geracao<?> geracao, long inicio) {
        Duration latencia = Duration.ofNanos(System.nanoTime() - inicio);
        return registro.gravar(MetadadosDaResposta.daChamada(provedor.nome(), geracao.modelo(), geracao.parametros(),
                prompt.versao(), geracao.uso(), precos, latencia));
    }
}
