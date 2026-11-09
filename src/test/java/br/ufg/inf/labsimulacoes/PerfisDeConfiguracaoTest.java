package br.ufg.inf.labsimulacoes;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class PerfisDeConfiguracaoTest {

    /** O Spring mantém a variável ausente como texto literal, que não é uma URL JDBC válida. */
    @Test
    void prodNaoSobeSemAUrlDoBanco() {
        var aplicacao = new SpringApplicationBuilder(LabSimulacoesApplication.class)
                .profiles("prod")
                .web(WebApplicationType.NONE);

        assertThatThrownBy(aplicacao::run).hasStackTraceContaining("must start with \"jdbc\"");
    }
}
