package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaginaDaFilaTest {

    private static final Instant CRIADA = Instant.parse("2026-11-23T12:00:00Z");

    @Test
    void sinalizacaoAMaisIndicaQueHaProximaPagina() {
        List<SinalizacaoPendente> lidas = sinalizacoes(3);

        var pagina = PaginaDaFila.lidaComUmaAMais(lidas, 2);

        assertThat(pagina.sinalizacoes()).containsExactly(lidas.get(0), lidas.get(1));
        assertThat(pagina.temProxima()).isTrue();
        assertThat(pagina.cursorDaProxima()).contains(CursorDaFila.apos(lidas.get(1)));
    }

    @Test
    void exatamenteOTamanhoEAUltimaPagina() {
        List<SinalizacaoPendente> lidas = sinalizacoes(2);

        var pagina = PaginaDaFila.lidaComUmaAMais(lidas, 2);

        assertThat(pagina.sinalizacoes()).isEqualTo(lidas);
        assertThat(pagina.temProxima()).isFalse();
        assertThat(pagina.cursorDaProxima()).isEmpty();
    }

    @Test
    void sinalizacoesDaPaginaNaoMudamDepoisDeMontada() {
        List<SinalizacaoPendente> lidas = new ArrayList<>(sinalizacoes(1));
        var pagina = PaginaDaFila.lidaComUmaAMais(lidas, 2);

        lidas.clear();

        assertThat(pagina.sinalizacoes()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, PaginaDaFila.TAMANHO_MAXIMO + 1})
    void tamanhoForaDosLimitesERecusado(int tamanho) {
        assertThatThrownBy(() -> PaginaDaFila.exigirTamanho(tamanho)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, PaginaDaFila.TAMANHO_MAXIMO})
    void tamanhoNosLimitesEAceito(int tamanho) {
        assertThatCode(() -> PaginaDaFila.exigirTamanho(tamanho)).doesNotThrowAnyException();
    }

    private static List<SinalizacaoPendente> sinalizacoes(int quantas) {
        var sinalizacoes = new ArrayList<SinalizacaoPendente>();
        for (int i = 1; i <= quantas; i++) {
            sinalizacoes.add(new SinalizacaoPendente(i, 2L, 3, "p", "r", TipoDeErro.ANACRONISMO,
                    GravidadePercebida.LEVE, "j", PerfilDaSinalizacao.CURADOR, 4, CRIADA.plusSeconds(i)));
        }
        return sinalizacoes;
    }
}
