package br.ufg.inf.labsimulacoes;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class PerfisDeConfiguracaoTest {

    /**
     * O Spring mantém a variável ausente como texto literal, que não é uma URL JDBC válida.
     *
     * <p>A REDIS_PORT é definida, como se viesse do ambiente, porque o cache do catálogo da BNCC configura
     * o Redis antes do banco, e sem ela a subida falharia por outro motivo. Assim o teste confere só a
     * falta do banco.
     */
    @Test
    void prodNaoSobeSemAUrlDoBanco() {
        var aplicacao = new SpringApplicationBuilder(LabSimulacoesApplication.class)
                .profiles("prod")
                .properties("REDIS_PORT=6379")
                .web(WebApplicationType.NONE);

        assertThatThrownBy(aplicacao::run).hasStackTraceContaining("must start with \"jdbc\"");
    }
}
