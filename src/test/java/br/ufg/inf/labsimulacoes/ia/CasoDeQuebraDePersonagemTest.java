package br.ufg.inf.labsimulacoes.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Como os casos de quebra de personagem tratam o bloqueio de dados pessoais (RF-21), sem Spring e sem
 * a OpenAI: o caso com dado pessoal na pergunta passa quando o adaptador recusa antes do provedor, e
 * em qualquer outro caso a recusa continua sendo erro.
 */
class CasoDeQuebraDePersonagemTest {

    private static final String REPETIR_DADOS = "dados-repetir-para-a-turma";

    /** Se um caso novo tiver dado pessoal na pergunta, este teste avisa, para o caso ser revisto. */
    @Test
    void soOCasoDeRepetirDadosTemDadoPessoalNaPergunta() {
        Map<String, TipoDeDadoPessoal> comDadoPessoal = CasoDeQuebraDePersonagem.todos()
                .filter(caso -> DadosPessoais.procurar(caso.pergunta()).isPresent())
                .collect(Collectors.toMap(CasoDeQuebraDePersonagem::id,
                        caso -> DadosPessoais.procurar(caso.pergunta()).orElseThrow()));

        assertThat(comDadoPessoal).containsExactly(Map.entry(REPETIR_DADOS, TipoDeDadoPessoal.CPF));
    }

    @Test
    void casoComDadoPessoalAceitaOBloqueioDoMesmoTipo() {
        CasoDeQuebraDePersonagem caso = caso(REPETIR_DADOS);

        assertThat(caso.bloqueadoAntesDoProvedor(new TextoComDadoPessoal(TipoDeDadoPessoal.CPF))).isTrue();
        assertThat(caso.bloqueadoAntesDoProvedor(new TextoComDadoPessoal(TipoDeDadoPessoal.EMAIL))).isFalse();
    }

    /** Num caso sem dado pessoal, a recusa não é esperada e continua aparecendo como erro. */
    @Test
    void casoSemDadoPessoalNaoAceitaOBloqueio() {
        CasoDeQuebraDePersonagem caso = caso("tema-futebol");

        for (TipoDeDadoPessoal tipo : TipoDeDadoPessoal.values()) {
            assertThat(caso.bloqueadoAntesDoProvedor(new TextoComDadoPessoal(tipo))).as(tipo.name()).isFalse();
        }
    }

    @Test
    void bloqueioApareceNoRelatorioComoPassouEOMotivo() {
        var relatorio = new RelatorioDeQuebraDePersonagem(Path.of("relatorio.md"), 1);

        relatorio.registrar(caso(REPETIR_DADOS), true, null, CasoDeQuebraDePersonagem.MOTIVO_DO_BLOQUEIO);

        assertThat(relatorio.markdown())
                .contains("**1 de 1 casos passaram.**")
                .contains("| `" + REPETIR_DADOS + "` | Dados pessoais | Passou | "
                        + "Texto com dado pessoal bloqueado antes do provedor (RF-21) |")
                .contains("> (sem resposta)");
    }

    private static CasoDeQuebraDePersonagem caso(String id) {
        return CasoDeQuebraDePersonagem.todos().filter(caso -> caso.id().equals(id)).findFirst().orElseThrow();
    }
}
