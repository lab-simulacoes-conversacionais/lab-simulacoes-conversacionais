package br.ufg.inf.labsimulacoes.cenarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsultaAoCatalogoTest {

    @Test
    void todasNaoTemNenhumFiltro() {
        assertThat(ConsultaAoCatalogo.todas()).isEqualTo(new ConsultaAoCatalogo(null, null, null, null, null, null));
    }

    @Test
    void filtrosSeCombinam() {
        var consulta = ConsultaAoCatalogo.porCodigo("EF08HI03")
                .comDescricao("Revolução")
                .comEtapa("EF")
                .comAno(8)
                .comComponente("História")
                .comLimite(10);

        assertThat(consulta).isEqualTo(new ConsultaAoCatalogo("EF08HI03", "Revolução", "EF", 8, "História", 10));
    }

    @Test
    void resultadoNaoMudaSeAListaDeAnosMudar() {
        var anos = new ArrayList<>(List.of(6, 7));
        var habilidade = new HabilidadeDoCatalogo("EF67LP08", "Texto.", "EF", anos, "Língua Portuguesa");

        anos.add(8);

        assertThat(habilidade.anos()).containsExactly(6, 7);
    }
}
