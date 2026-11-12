package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.eventos.ConteudoBloqueado.Origem;
import br.ufg.inf.labsimulacoes.eventos.ConteudoBloqueado;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regras do caso e do evento de conteúdo bloqueado (RN-20). */
class CasoBloqueadoTest {

    private static final Instant MOMENTO = Instant.parse("2026-11-11T13:45:10Z");

    @Test
    void soOCasoDaRespostaTemTrecho() {
        assertThatThrownBy(() -> new CasoBloqueado(1, 2, Origem.RESPOSTA, "P", null, List.of(), null, MOMENTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CasoBloqueado(1, 2, Origem.PERGUNTA, "P", "T", List.of(), null, MOMENTO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void camposObrigatoriosECategoriasImutaveis() {
        assertThatThrownBy(() -> new CasoBloqueado(1, 2, null, "P", null, List.of(), null, MOMENTO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CasoBloqueado(1, 2, Origem.PERGUNTA, null, null, List.of(), null, MOMENTO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CasoBloqueado(1, 2, Origem.PERGUNTA, "P", null, List.of(), null, null))
                .isInstanceOf(NullPointerException.class);
        List<String> categorias = new ArrayList<>(List.of("hate"));
        var caso = new CasoBloqueado(1, 2, Origem.PERGUNTA, "P", null, categorias, null, MOMENTO);
        categorias.add("violence");
        assertThat(caso.categorias()).containsExactly("hate");
        assertThat(new CasoBloqueado(1, 2, Origem.PERGUNTA, "P", null, null, null, MOMENTO).categorias()).isEmpty();
    }

    @Test
    void eventoSegueAsMesmasRegras() {
        var origem = ConteudoBloqueado.Origem.RESPOSTA;
        assertThatThrownBy(() -> new ConteudoBloqueado(1, 2, origem, "P", null, List.of(), null, MOMENTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConteudoBloqueado(
                        1, 2, ConteudoBloqueado.Origem.PERGUNTA, "P", "T", List.of(), null, MOMENTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConteudoBloqueado(
                        1, 2, ConteudoBloqueado.Origem.PERGUNTA, "P", null, List.of(), 5L, MOMENTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConteudoBloqueado(1, 2, null, "P", null, List.of(), null, MOMENTO))
                .isInstanceOf(NullPointerException.class);
        assertThat(new ConteudoBloqueado(1, 2, origem, "P", "T", null, 5L, MOMENTO).categorias()).isEmpty();
    }

    /** O evento repetido no fim da resposta é a mesma ocorrência, agora com os metadados. */
    @Test
    void eventoComMetadadosEAMesmaOcorrencia() {
        var semMetadados = new ConteudoBloqueado(
                1, 2, ConteudoBloqueado.Origem.RESPOSTA, "P", "T", List.of("hate"), null, MOMENTO);

        assertThat(semMetadados.comMetadados(5)).isEqualTo(new ConteudoBloqueado(
                1, 2, ConteudoBloqueado.Origem.RESPOSTA, "P", "T", List.of("hate"), 5L, MOMENTO));
    }
}
