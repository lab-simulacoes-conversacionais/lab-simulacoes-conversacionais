package br.ufg.inf.labsimulacoes.acesso;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.GestaoDeParametros;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Critérios de aceite da issue 7 sobre os parâmetros: o que o administrador altera chega aos outros
 * módulos pela API do Acesso, sem reiniciar a aplicação. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class ParametrosDeUsoTest {

    @Autowired
    private ParametrosDeUso parametros;

    @Autowired
    private GestaoDeParametros gestao;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void comecarSemParametros() {
        jdbc.update("DELETE FROM parametro");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin@escola.br", null, "ROLE_ADMINISTRADOR"));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void semAlteracaoOLimiteEDeQuarentaPerguntasESemCustoMaximo() {
        assertThat(parametros.limiteDePerguntasPorSessao()).isEqualTo(40);
        assertThat(parametros.custoDiarioMaximoUsd()).isEmpty();
    }

    /** A próxima sessão a ler o limite já recebe o valor novo. */
    @Test
    void alterarOLimiteValeParaAProximaLeitura() {
        int antes = parametros.limiteDePerguntasPorSessao();

        gestao.alterar(25, null);

        assertThat(antes).isEqualTo(40);
        assertThat(parametros.limiteDePerguntasPorSessao()).isEqualTo(25);
    }

    @Test
    void custoDefinidoPeloAdministradorChegaAosOutrosModulos() {
        gestao.alterar(40, new BigDecimal("7.50"));

        assertThat(parametros.custoDiarioMaximoUsd()).contains(new BigDecimal("7.5"));
    }
}
