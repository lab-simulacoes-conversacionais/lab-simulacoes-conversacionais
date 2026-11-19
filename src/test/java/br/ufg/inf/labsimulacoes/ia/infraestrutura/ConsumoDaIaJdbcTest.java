package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.ia.ConsumoDaIa;
import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import br.ufg.inf.labsimulacoes.ia.ResumoDoConsumo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Totais de consumo da IA no PostgreSQL real (RNF-13), pela API do módulo. Os metadados ficam num ano
 * em que nenhum outro teste grava, e as referências são sorteadas, para não somar o que outros testes
 * deixaram no banco. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class ConsumoDaIaJdbcTest {

    private static final Instant INICIO = Instant.parse("2031-03-10T03:00:00Z");
    private static final Instant FIM = Instant.parse("2031-03-11T03:00:00Z");

    @Autowired
    private ConsumoDaIa consumo;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void custoEntreSomaTokensECustoDoIntervalo() {
        inserir(INICIO, 100, 20, "0.000200", FinalidadeDoConsumo.FALA_EM_SALA, 1L);
        inserir(FIM.minusNanos(1_000), 300, 40, "0.000500", FinalidadeDoConsumo.SUGESTAO, null);
        inserir(INICIO.plusSeconds(60), 50, 5, "0.000100", null, null);

        ResumoDoConsumo resumo = consumo.custoEntre(INICIO, FIM);

        assertThat(resumo.respostas()).isEqualTo(3);
        assertThat(resumo.tokensEntrada()).isEqualTo(450);
        assertThat(resumo.tokensSaida()).isEqualTo(65);
        assertThat(resumo.custo()).isEqualByComparingTo("0.000800");
        assertThat(resumo.respostasSemCusto()).isZero();
    }

    /** O início entra e o fim não: o mesmo instante nunca conta em dois dias. */
    @Test
    void inicioEntraEFimNaoEntra() {
        inserir(INICIO.minusNanos(1_000), 1, 1, "1.000000", null, null);
        inserir(FIM, 1, 1, "2.000000", null, null);
        inserir(INICIO, 1, 1, "0.000300", null, null);

        assertThat(consumo.custoEntre(INICIO, FIM).custo()).isEqualByComparingTo("0.000300");
    }

    /** Sem tokens informados o custo fica nulo: não soma, mas é contado à parte. */
    @Test
    void respostaSemCustoEContadaAParte() {
        inserir(INICIO, null, null, null, FinalidadeDoConsumo.FALA_EM_SALA, 1L);
        inserir(INICIO, 10, 10, "0.000060", FinalidadeDoConsumo.FALA_EM_SALA, 1L);

        ResumoDoConsumo resumo = consumo.custoEntre(INICIO, FIM);

        assertThat(resumo.respostas()).isEqualTo(2);
        assertThat(resumo.respostasSemCusto()).isEqualTo(1);
        assertThat(resumo.custo()).isEqualByComparingTo("0.000060");
        assertThat(resumo.tokensEntrada()).isEqualTo(10);
    }

    @Test
    void intervaloSemConsumoVemZerado() {
        assertThat(consumo.custoEntre(INICIO, FIM)).isEqualTo(new ResumoDoConsumo(0, 0, 0, BigDecimal.ZERO, 0));
    }

    @Test
    void intervaloInvertidoERecusado() {
        assertThatThrownBy(() -> consumo.custoEntre(FIM, INICIO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consumo.custoEntre(INICIO, INICIO)).isInstanceOf(IllegalArgumentException.class);
    }

    /** O custo de uma sessão é o da finalidade e da referência dela, nada de outra sessão ou finalidade. */
    @Test
    void daReferenciaSomaSoAFinalidadeEAReferencia() {
        long sessao = referenciaSorteada();
        inserir(INICIO, 100, 10, "0.000150", FinalidadeDoConsumo.FALA_EM_SALA, sessao);
        inserir(FIM.plusSeconds(86_400), 200, 20, "0.000300", FinalidadeDoConsumo.FALA_EM_SALA, sessao);
        inserir(INICIO, null, null, null, FinalidadeDoConsumo.FALA_EM_SALA, sessao);
        inserir(INICIO, 999, 999, "9.000000", FinalidadeDoConsumo.FALA_EM_SALA, sessao + 1);
        inserir(INICIO, 999, 999, "9.000000", FinalidadeDoConsumo.TESTE_DO_CURADOR, sessao);

        ResumoDoConsumo resumo = consumo.daReferencia(FinalidadeDoConsumo.FALA_EM_SALA, sessao);

        assertThat(resumo).isEqualTo(new ResumoDoConsumo(3, 300, 30, resumo.custo(), 1));
        assertThat(resumo.custo()).isEqualByComparingTo("0.000450");
    }

    @Test
    void referenciaSemConsumoVemZerada() {
        ResumoDoConsumo resumo = consumo.daReferencia(FinalidadeDoConsumo.FALA_EM_SALA, referenciaSorteada());

        assertThat(resumo.respostas()).isZero();
        assertThat(resumo.custo()).isEqualByComparingTo("0");
    }

    private void inserir(
            Instant em, Integer entrada, Integer saida, String custo, FinalidadeDoConsumo finalidade, Long referencia) {
        jdbc.update("""
                INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt, tokens_entrada, tokens_saida, custo,
                                              finalidade, referencia_id, criado_em)
                VALUES ('openai', 'gpt-6.1-terra', 'personagem-v1', ?, ?, ?, ?, ?, ?)""",
                entrada, saida, custo == null ? null : new BigDecimal(custo),
                finalidade == null ? null : finalidade.name(), referencia, OffsetDateTime.ofInstant(em, ZoneOffset.UTC));
    }

    private static long referenciaSorteada() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE - 1);
    }
}
