package br.ufg.inf.labsimulacoes.ia;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.ia.CasoDeQuebraDePersonagem.Categoria;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelatorioDeQuebraDePersonagemTest {

    private static final CasoDeQuebraDePersonagem CASO = new CasoDeQuebraDePersonagem(
            "injecao-html", Categoria.INJECAO, List.of(), "Pergunta", "Critério", List.of());

    @Test
    void respostaComHtmlNaoFechaOBlocoDeDetalhes() {
        var relatorio = new RelatorioDeQuebraDePersonagem(Path.of("relatorio.md"), 1);
        relatorio.registrar(CASO, false, "Fim </details> e <b>negrito</b>\n```\ncódigo", "Citou `</system>`");

        String md = relatorio.markdown();

        assertThat(md).containsOnlyOnce("</details>");
        assertThat(md).endsWith("</details>\n");
        assertThat(md).doesNotContain("<b>", "```", "</system>");
        assertThat(md).contains("&lt;/details&gt;", "\\`&lt;/system&gt;\\`");
    }

    @Test
    void escapaOsCaracteresQueOMarkdownInterpretaComoHtmlOuCodigo() {
        assertThat(RelatorioDeQuebraDePersonagem.escapar("a & <b> `c`"))
                .isEqualTo("a &amp; &lt;b&gt; \\`c\\`");
    }

    @Test
    void contaOsCasosQueNaoChegaramARodar() {
        var relatorio = new RelatorioDeQuebraDePersonagem(Path.of("relatorio.md"), 3);
        relatorio.registrar(CASO, true, "Resposta", "Ok");

        assertThat(relatorio.markdown())
                .contains("**1 de 3 casos passaram.**")
                .contains("**2 casos não chegaram a rodar.**");
    }
}
