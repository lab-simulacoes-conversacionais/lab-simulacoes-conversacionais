package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.ia.dominio.MetadadosDaResposta;
import br.ufg.inf.labsimulacoes.ia.dominio.RegistroDeMetadados;
import br.ufg.inf.labsimulacoes.ia.dominio.TabelaDePrecos;
import br.ufg.inf.labsimulacoes.ia.dominio.UsoDoModelo;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Gravação e leitura dos metadados no banco real. Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class RegistroDeMetadadosJdbcTest {

    private static final TabelaDePrecos PRECOS = new TabelaDePrecos(new BigDecimal("1.00"), new BigDecimal("5.00"));

    @Autowired
    private RegistroDeMetadados registro;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void metadadosGravadosVoltamIguaisDoBanco() {
        MetadadosDaResposta gravado = registro.gravar(MetadadosDaResposta.daChamada(
                "openai", "gpt-6.1-terra", Map.of("reasoning_effort", "low", "max_tokens", 400),
                "provisorio-1", new UsoDoModelo(316, 97), PRECOS, Duration.ofMillis(1960)));

        MetadadosDaResposta lido = registro.buscar(gravado.id()).orElseThrow();

        assertThat(gravado.id()).isNotNull();
        assertThat(lido).usingRecursiveComparison().ignoringFields("custo").isEqualTo(gravado);
        assertThat(lido.custo()).isEqualByComparingTo("0.000801");
        assertThat(jdbc.queryForObject(
                        "SELECT criado_em IS NOT NULL FROM resposta_ia_meta WHERE id = ?", Boolean.class, lido.id()))
                .isTrue();
    }

    @Test
    void tokensECustoDesconhecidosFicamNulos() {
        MetadadosDaResposta gravado = registro.gravar(MetadadosDaResposta.daChamada(
                "openai", "gpt-6.1-terra", Map.of(), "provisorio-1",
                UsoDoModelo.desconhecido(), PRECOS, Duration.ofMillis(5)));

        MetadadosDaResposta lido = registro.buscar(gravado.id()).orElseThrow();

        assertThat(lido.uso()).isEqualTo(UsoDoModelo.desconhecido());
        assertThat(lido.custo()).isNull();
        assertThat(lido.parametros()).isEmpty();
    }

    @Test
    void idInexistenteVoltaVazio() {
        assertThat(registro.buscar(Long.MAX_VALUE)).isEmpty();
    }
}
