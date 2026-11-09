package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.cenarios.dominio.CatalogoInvalido;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.LeituraDeHabilidades;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Sem Spring: o leitor só depende do Jackson e dos arquivos. */
class LeitorDoEnsinoFundamentalTest {

    private static final String FIXTURES = "bncc-fixtures/";

    /** O ObjectMapper puro recusa campos desconhecidos; o leitor precisa ignorá-los mesmo assim. */
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void arquivoRealTemAsHabilidadesDoEnsinoFundamental() {
        LeituraDeHabilidades leitura = real().ler();

        assertThat(leitura.etapa()).isEqualTo("EF");
        assertThat(leitura.habilidades()).hasSize(1304);
        assertThat(leitura.ignoradas()).isZero();
        assertThat(leitura.habilidades()).extracting(HabilidadeBncc::codigo).doesNotHaveDuplicates();
    }

    @Test
    void arquivoRealTraduzOComponenteELeOsAnos() {
        List<HabilidadeBncc> habilidades = real().ler().habilidades();

        assertThat(habilidades).filteredOn(h -> h.codigo().equals("EF08HI03")).singleElement().satisfies(h -> {
            assertThat(h.descricao()).startsWith("Analisar os impactos da Revolução Industrial");
            assertThat(h.etapa()).isEqualTo("EF");
            assertThat(h.anosComoTexto()).isEqualTo("8");
            assertThat(h.componente()).isEqualTo("História");
        });
        assertThat(habilidades).filteredOn(h -> h.codigo().equals("EF67LP08")).singleElement()
                .satisfies(h -> assertThat(h.anosComoTexto()).isEqualTo("6,7"));
        assertThat(habilidades).filteredOn(h -> h.codigo().equals("EF15LP01")).singleElement()
                .satisfies(h -> assertThat(h.anosComoTexto()).isEqualTo("1,2,3,4,5"));
    }

    @Test
    void habilidadeNaoVigenteEIgnoradaEContada() {
        LeituraDeHabilidades leitura = fixture("habilidades-borda.json").ler();

        assertThat(leitura.habilidades()).extracting(HabilidadeBncc::codigo).containsExactly("EF08HI03", "EF67LP08");
        assertThat(leitura.ignoradas()).isEqualTo(1);
    }

    @Test
    void anosForaDeOrdemNoArquivoFicamOrdenados() {
        var bloco = fixture("habilidades-borda.json").ler().habilidades().get(1);

        assertThat(bloco.anos()).containsExactly(6, 7);
        assertThat(bloco.componente()).isEqualTo("Língua Portuguesa");
    }

    @Test
    void componenteDesconhecidoFalhaComMensagemClara() {
        assertThatThrownBy(() -> fixture("componente-desconhecido.json").ler())
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("ef-comp-xx")
                .hasMessageContaining("EF08XX01")
                .hasMessageContaining("estrutura.json")
                .hasMessageContaining("nada do catálogo foi gravado");
    }

    @Test
    void habilidadeSemVigenciaFalha() {
        assertThatThrownBy(() -> fixture("sem-vigencia.json").ler())
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("vigência");
    }

    @Test
    void codigoInvalidoFalhaComOCodigoNaMensagem() {
        assertThatThrownBy(() -> fixture("codigo-invalido.json").ler())
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("ef8hi3")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arquivoSemAListaDeHabilidadesFalha() {
        assertThatThrownBy(() -> fixture("sem-lista.json").ler())
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("\"habilidades\"");
    }

    @Test
    void estruturaSemComponentesFalha() {
        var leitor = new LeitorDoEnsinoFundamental(
                json, recurso("habilidades-borda.json"), recurso("sem-lista.json"));

        assertThatThrownBy(leitor::ler)
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("componentes_curriculares");
    }

    @Test
    void jsonMalformadoFalha() {
        assertThatThrownBy(() -> fixture("malformado.json").ler())
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("Não foi possível ler malformado.json");
    }

    @Test
    void arquivoInexistenteFalha() {
        var leitor = new LeitorDoEnsinoFundamental(
                json, recurso("nao-existe.json"), recurso("estrutura.json"));

        assertThatThrownBy(leitor::ler)
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("Não foi possível ler");
    }

    private LeitorDoEnsinoFundamental real() {
        return new LeitorDoEnsinoFundamental(
                json, new ClassPathResource("bncc/ensino-fundamental.json"), new ClassPathResource("bncc/estrutura.json"));
    }

    private LeitorDoEnsinoFundamental fixture(String habilidades) {
        return new LeitorDoEnsinoFundamental(json, recurso(habilidades), recurso("estrutura.json"));
    }

    private static ClassPathResource recurso(String nome) {
        return new ClassPathResource(FIXTURES + nome);
    }
}
