package br.ufg.inf.labsimulacoes.acesso.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TurmaTest {

    /** RN-32: um campo novo na turma precisa passar por esta lista, e nenhum pode ser de estudante. */
    @Test
    void turmaTemSoOsCamposDeIdentificacao() {
        assertThat(Arrays.stream(Turma.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("id", "escola", "anoSerie", "turno", "rotulo");
    }

    @Test
    void camposFicamSemEspacosNasPontas() {
        Turma turma = Turma.nova(" Escola Estadual ", " 8º ano ", " Matutino ", " 8º A ");

        assertThat(turma).isEqualTo(new Turma(null, "Escola Estadual", "8º ano", "Matutino", "8º A"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void camposObrigatoriosPrecisamEstarPreenchidos(String vazio) {
        assertThatThrownBy(() -> Turma.nova(vazio, "8º ano", "Matutino", "8º A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escola");
        assertThatThrownBy(() -> Turma.nova("Escola", vazio, "Matutino", "8º A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ano/série");
        assertThatThrownBy(() -> Turma.nova("Escola", "8º ano", vazio, "8º A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("turno");
        assertThatThrownBy(() -> Turma.nova("Escola", "8º ano", "Matutino", vazio))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rótulo");
    }

    @Test
    void alterarMantemOIdentificador() {
        Turma turma = new Turma(7L, "Escola", "8º ano", "Matutino", "8º A");

        Turma alterada = turma.alterar("Escola", "8º ano", "Vespertino", "8º B");

        assertThat(alterada).isEqualTo(new Turma(7L, "Escola", "8º ano", "Vespertino", "8º B"));
    }

    @Test
    void alterarTambemValidaOsCampos() {
        Turma turma = new Turma(7L, "Escola", "8º ano", "Matutino", "8º A");

        assertThatThrownBy(() -> turma.alterar("Escola", "8º ano", "Matutino", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mesmaIdentificacaoIgnoraOIdentificador() {
        Turma turma = new Turma(1L, "Escola", "8º ano", "Matutino", "8º A");

        assertThat(turma.mesmaIdentificacaoQue(Turma.nova("Escola", "8º ano", "Matutino", "8º A"))).isTrue();
    }

    @Test
    void qualquerCampoDiferenteEOutraIdentificacao() {
        Turma turma = Turma.nova("Escola", "8º ano", "Matutino", "8º A");

        assertThat(turma.mesmaIdentificacaoQue(Turma.nova("Outra", "8º ano", "Matutino", "8º A"))).isFalse();
        assertThat(turma.mesmaIdentificacaoQue(Turma.nova("Escola", "9º ano", "Matutino", "8º A"))).isFalse();
        assertThat(turma.mesmaIdentificacaoQue(Turma.nova("Escola", "8º ano", "Noturno", "8º A"))).isFalse();
        assertThat(turma.mesmaIdentificacaoQue(Turma.nova("Escola", "8º ano", "Matutino", "8º B"))).isFalse();
    }
}
