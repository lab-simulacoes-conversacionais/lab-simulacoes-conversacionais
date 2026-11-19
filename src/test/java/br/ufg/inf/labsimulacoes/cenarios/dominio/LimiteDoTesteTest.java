package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Teto de perguntas de teste por curador e versão (RNF-13). */
class LimiteDoTesteTest {

    private final LimiteDoTeste limite = new LimiteDoTeste(40);

    @Test
    void cabeAteAUltimaPerguntaDoTeto() {
        assertThatCode(() -> limite.exigirDentro(0)).doesNotThrowAnyException();
        assertThatCode(() -> limite.exigirDentro(39)).doesNotThrowAnyException();
    }

    @Test
    void comOTetoCheioAProximaERecusada() {
        assertThatThrownBy(() -> limite.exigirDentro(40))
                .isInstanceOf(LimiteDoTesteAtingido.class)
                .hasMessage("Você já fez 40 perguntas de teste nesta versão do cenário.");
    }

    /** O teto precisa caber as 5 perguntas que o RN-06 exige, ou nenhum cenário seria aprovado. */
    @Test
    void tetoAbaixoDoMinimoParaAprovarERecusado() {
        assertThatThrownBy(() -> new LimiteDoTeste(PerguntasDeTeste.MINIMO_PARA_APROVAR - 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new LimiteDoTeste(PerguntasDeTeste.MINIMO_PARA_APROVAR)).doesNotThrowAnyException();
    }
}
