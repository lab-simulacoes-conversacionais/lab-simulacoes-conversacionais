package br.ufg.inf.labsimulacoes.ia.aplicacao;

import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
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
import java.time.Duration;
import java.util.function.Consumer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Porta do adaptador de IA: monta o prompt, chama o provedor e grava os metadados de cada resposta
 * gerada (RNF-10, RNF-13).
 *
 * <p>Não abre transação: a resposta em streaming pode levar segundos, e a gravação dos metadados é uma
 * linha só. A latência conta do pedido ao provedor até o fim da resposta.
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
public class ChamadasAoProvedor implements AdaptadorDeIa {

    private final ProvedorDeIa provedor;
    private final MontadorDePrompt montador;
    private final RegistroDeMetadados registro;
    private final TabelaDePrecos precos;

    ChamadasAoProvedor(
            ProvedorDeIa provedor, MontadorDePrompt montador, RegistroDeMetadados registro, TabelaDePrecos precos) {
        this.provedor = provedor;
        this.montador = montador;
        this.registro = registro;
        this.precos = precos;
    }

    @Override
    public RespostaDoPersonagem responder(PedidoDeFala pedido, Consumer<String> aoReceberTrecho) {
        if (pedido == null || aoReceberTrecho == null) {
            throw new IllegalArgumentException("O pedido e quem recebe os trechos são obrigatórios");
        }
        PromptMontado prompt = montador.daFala(pedido);
        long inicio = System.nanoTime();
        Geracao<String> geracao = provedor.conversar(prompt, aoReceberTrecho);
        return new RespostaDoPersonagem(geracao.conteudo(), registrar(prompt, geracao, inicio).id());
    }

    @Override
    public SugestaoDeCenario sugerirCenario(PedidoDeSugestao pedido) {
        if (pedido == null) {
            throw new IllegalArgumentException("O pedido de sugestão é obrigatório");
        }
        PromptMontado prompt = montador.daSugestao(pedido);
        long inicio = System.nanoTime();
        Geracao<RoteiroSugerido> geracao = provedor.sugerirRoteiro(prompt);
        return new SugestaoDeCenario(geracao.conteudo(), registrar(prompt, geracao, inicio).id());
    }

    @Override
    public ResultadoDaModeracao moderar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("O texto a moderar é obrigatório");
        }
        return provedor.moderar(texto);
    }

    private MetadadosDaResposta registrar(PromptMontado prompt, Geracao<?> geracao, long inicio) {
        Duration latencia = Duration.ofNanos(System.nanoTime() - inicio);
        return registro.gravar(MetadadosDaResposta.daChamada(provedor.nome(), geracao.modelo(), geracao.parametros(),
                prompt.versao(), geracao.uso(), precos, latencia));
    }
}
