package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.ObservacoesGravadas;
import br.ufg.inf.labsimulacoes.ObservacoesGravadas.Observada;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.EtapasDaConversa.Etapa;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.EtapasDaConversa.EtapaEmAndamento;
import java.util.List;
import org.junit.jupiter.api.Test;

class EtapasDaConversaTest {

    private final ObservacoesGravadas observacoes = new ObservacoesGravadas();
    private final EtapasDaConversa etapas = new EtapasDaConversa(observacoes.registro());

    @Test
    void medirDevolveOResultadoEObservaAEtapa() {
        String resultado = etapas.medir(Etapa.PREPARO_DA_RESPOSTA, () -> "pronto");

        assertThat(resultado).isEqualTo("pronto");
        assertThat(paradas()).singleElement().satisfies(o -> {
            assertThat(o.valor(EtapasDaConversa.CHAVE)).isEqualTo("preparo-da-resposta");
            assertThat(o.erro()).isNull();
        });
    }

    @Test
    void erroDaAcaoFicaNaEtapaESaiSemMudanca() {
        var falha = new IllegalStateException("fora do ar");

        assertThatThrownBy(() -> etapas.medir(Etapa.GRAVACAO_DA_PERGUNTA, (Runnable) () -> {
                    throw falha;
                }))
                .isSameAs(falha);
        assertThat(paradas()).singleElement().extracting(Observada::erro).isSameAs(falha);
    }

    @Test
    void etapaIniciadaParaUmaVezSo() {
        EtapaEmAndamento espera = etapas.iniciar(Etapa.ESPERA_DA_THREAD);

        espera.parar();
        espera.parar();
        espera.falhar(new IllegalStateException("depois de parar"));

        assertThat(paradas()).singleElement().extracting(Observada::erro).isNull();
    }

    @Test
    void etapaIniciadaParaComOErro() {
        var falha = new IllegalStateException("recusada");
        EtapaEmAndamento preparo = etapas.iniciar(Etapa.PREPARO_DA_RESPOSTA);

        preparo.falhar(falha);
        preparo.parar();

        assertThat(paradas()).singleElement().extracting(Observada::erro).isSameAs(falha);
    }

    private List<Observada> paradas() {
        return observacoes.paradas(EtapasDaConversa.OBSERVACAO);
    }
}
