package br.ufg.inf.labsimulacoes.metricas.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.metricas.dominio.AlertaDeCusto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conferência do custo diário da IA (RNF-13), com PostgreSQL real e o fuso de São Paulo da
 * configuração. Critério de aceite da #31: o alerta dispara em teste com limite baixo. O consumo fica num
 * ano em que nenhum outro teste grava, e a conferência agendada está desligada nos testes. Cada teste
 * desfaz o que gravou, inclusive o custo diário máximo.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class VerificacaoDoCustoDiarioTest {

    /** 10 de maio de 2032, 12h em São Paulo. */
    private static final Instant MEIO_DO_DIA = Instant.parse("2032-05-10T15:00:00Z");
    private static final LocalDate DIA = LocalDate.of(2032, 5, 10);

    @Autowired
    private VerificacaoDoCustoDiario verificacao;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void semLimite() {
        jdbc.update("DELETE FROM parametro WHERE chave = 'custo_diario_maximo_usd'");
    }

    @Test
    void limiteBaixoDisparaUmAlerta(CapturedOutput saida) {
        definirLimite("0.01");
        consumir(MEIO_DO_DIA, "0.02");

        var alerta = verificacao.verificar(MEIO_DO_DIA);

        assertThat(alerta).contains(new AlertaDeCusto(DIA, alerta.orElseThrow().custo(), new BigDecimal("0.01")));
        assertThat(alerta.orElseThrow().custo()).isEqualByComparingTo("0.02");
        assertThat(alertasDoDia()).isEqualTo(1);
        assertThat(saida.getOut()).contains("WARN").contains("passou do custo diário máximo").contains("2032-05-10");
    }

    /** Um alerta por dia: a segunda conferência não grava outro nem repete o aviso no log. */
    @Test
    void segundaConferenciaNaoDuplicaOAlerta(CapturedOutput saida) {
        definirLimite("0.01");
        consumir(MEIO_DO_DIA, "0.02");
        verificacao.verificar(MEIO_DO_DIA);
        consumir(MEIO_DO_DIA.plusSeconds(60), "0.50");

        assertThat(verificacao.verificar(MEIO_DO_DIA.plusSeconds(120))).isEmpty();

        assertThat(alertasDoDia()).isEqualTo(1);
        assertThat(saida.getOut().split("passou do custo diário máximo", -1)).hasSize(2);
    }

    /** O alerta não se repete no mesmo dia nem quando o administrador muda o limite. */
    @Test
    void mudarOLimiteNoMesmoDiaNaoRepeteOAlerta() {
        definirLimite("0.01");
        consumir(MEIO_DO_DIA, "0.02");
        verificacao.verificar(MEIO_DO_DIA);
        definirLimite("0.015");

        assertThat(verificacao.verificar(MEIO_DO_DIA.plusSeconds(60))).isEmpty();

        assertThat(jdbc.queryForObject("SELECT limite FROM alerta_custo_diario WHERE dia = ?", BigDecimal.class, DIA))
                .isEqualByComparingTo("0.01");
    }

    /** Sem custo diário máximo definido, não há alerta, por maior que seja o custo. */
    @Test
    void semLimiteNaoAlerta() {
        consumir(MEIO_DO_DIA, "1000");

        assertThat(verificacao.verificar(MEIO_DO_DIA)).isEmpty();

        assertThat(alertasDoDia()).isZero();
    }

    @Test
    void custoAbaixoDoLimiteNaoAlerta() {
        definirLimite("5");
        consumir(MEIO_DO_DIA, "0.02");

        assertThat(verificacao.verificar(MEIO_DO_DIA)).isEmpty();

        assertThat(alertasDoDia()).isZero();
    }

    /**
     * O dia vira à meia-noite de São Paulo (03h em UTC): o consumo das 23h30 do dia 10 conta no dia 10,
     * e a conferência das 00h30 do dia 11 começa do zero.
     */
    @Test
    void diaViraAMeiaNoiteNoFuso() {
        definirLimite("0.01");
        consumir(Instant.parse("2032-05-11T02:30:00Z"), "0.02");

        assertThat(verificacao.verificar(Instant.parse("2032-05-11T03:30:00Z"))).isEmpty();
        assertThat(alertas(DIA.plusDays(1))).isZero();

        assertThat(verificacao.verificar(Instant.parse("2032-05-11T02:59:59Z")))
                .hasValueSatisfying(alerta -> assertThat(alerta.dia()).isEqualTo(DIA));
    }

    /** A cada dia, um alerta novo. */
    @Test
    void diaSeguinteTemOSeuAlerta() {
        definirLimite("0.01");
        consumir(MEIO_DO_DIA, "0.02");
        verificacao.verificar(MEIO_DO_DIA);
        Instant amanha = MEIO_DO_DIA.plusSeconds(86_400);
        consumir(amanha, "0.02");

        assertThat(verificacao.verificar(amanha)).isPresent();

        assertThat(alertas(DIA.plusDays(1))).isEqualTo(1);
    }

    /** A conferência agendada nunca derruba a aplicação: a falha fica no log. */
    @Test
    void conferenciaAgendadaNaoLancaExcecao() {
        verificacao.verificarAgora();
    }

    private void definirLimite(String valor) {
        jdbc.update("""
                INSERT INTO parametro (chave, valor) VALUES ('custo_diario_maximo_usd', ?)
                ON CONFLICT (chave) DO UPDATE SET valor = excluded.valor""", valor);
    }

    private void consumir(Instant em, String custo) {
        jdbc.update("""
                INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt, tokens_entrada, tokens_saida, custo,
                                              finalidade, criado_em)
                VALUES ('openai', 'gpt-6.1-terra', 'personagem-v1', 10, 10, ?, 'FALA_EM_SALA', ?)""",
                new BigDecimal(custo), OffsetDateTime.ofInstant(em, ZoneOffset.UTC));
    }

    private int alertasDoDia() {
        return alertas(DIA);
    }

    private int alertas(LocalDate dia) {
        return jdbc.queryForObject("SELECT count(*) FROM alerta_custo_diario WHERE dia = ?", Integer.class, dia);
    }
}
