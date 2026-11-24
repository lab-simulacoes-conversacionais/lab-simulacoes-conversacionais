package br.ufg.inf.labsimulacoes.ia.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.ia.TextoComDadoPessoal;
import br.ufg.inf.labsimulacoes.ia.TipoDeDadoPessoal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/** O bloqueio de dados pessoais com a métrica (RN-13), sem Spring. Os dados são inventados. */
class BloqueioPorPadroesTest {

    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private final BloqueioPorPadroes bloqueio = new BloqueioPorPadroes(metricas);

    @Test
    void textoSemDadoPessoalPassaSemContar() {
        assertThatCode(() -> bloqueio.exigirSemDadoPessoal("Como voou o 14-bis em 1906?")).doesNotThrowAnyException();
        assertThatCode(() -> bloqueio.exigirSemDadoPessoal(null)).doesNotThrowAnyException();
        assertThatCode(() -> bloqueio.exigirSemDadoPessoal(" ")).doesNotThrowAnyException();
        assertThat(metricas.find(BloqueioPorPadroes.METRICA).counters()).isEmpty();
    }

    @Test
    void textoComDadoPessoalEBloqueadoEContaPeloTipo() {
        assertThatThrownBy(() -> bloqueio.exigirSemDadoPessoal("Moro na Rua das Flores, 123"))
                .isInstanceOf(TextoComDadoPessoal.class);
        assertThatThrownBy(() -> bloqueio.exigirSemDadoPessoal("Rua das Flores, 456"))
                .isInstanceOf(TextoComDadoPessoal.class);

        assertThat(metricas.get(BloqueioPorPadroes.METRICA).tag(BloqueioPorPadroes.TIPO, "endereco").counter().count())
                .isEqualTo(2.0);
    }

    /** O aviso nomeia o tipo e orienta a reescrever; a exceção não guarda o texto. */
    @Test
    void avisoNomeiaOTipoSemRepetirODado() {
        var erro = catchErro("Escreva para fulano@exemplo.com");

        assertThat(erro.tipo()).isEqualTo(TipoDeDadoPessoal.EMAIL);
        assertThat(erro.aviso("A pergunta")).isEqualTo("A pergunta parece ter um dado pessoal (e-mail). Reescreva sem "
                + "e-mail, telefone, CPF ou endereço, e não digite nomes de estudantes.");
        assertThat(erro.getMessage()).doesNotContain("fulano").doesNotContain("exemplo");
        assertThat(new TextoComDadoPessoal(TipoDeDadoPessoal.ENDERECO).aviso("O tema"))
                .startsWith("O tema parece ter um dado pessoal (endereço).");
    }

    private TextoComDadoPessoal catchErro(String texto) {
        try {
            bloqueio.exigirSemDadoPessoal(texto);
        } catch (TextoComDadoPessoal erro) {
            return erro;
        }
        throw new AssertionError("O texto deveria ter sido bloqueado");
    }
}
