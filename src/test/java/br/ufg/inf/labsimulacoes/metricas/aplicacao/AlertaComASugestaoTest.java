package br.ufg.inf.labsimulacoes.metricas.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.CenarioDaConversa;
import br.ufg.inf.labsimulacoes.ia.CenariosDeTeste;
import br.ufg.inf.labsimulacoes.ia.ConsumoDaIa;
import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.PedidoDeSugestao;
import br.ufg.inf.labsimulacoes.ia.RoteiroSugerido;
import br.ufg.inf.labsimulacoes.ia.dominio.Geracao;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.dominio.UsoDoModelo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * RNF-13, revisão do PR #95: a sugestão roda no nível de serviço padrão e é precificada pela tarifa dele,
 * então não leva o custo do dia a um alerta falso. Com o adaptador de IA real e só o provedor simulado.
 * As respostas geradas são levadas a um dia em que nenhum outro teste grava, para o custo do dia ser só o
 * delas. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class AlertaComASugestaoTest {

    /** 10 de março de 2034, 12h em São Paulo. */
    private static final Instant DIA_DO_TESTE = Instant.parse("2034-03-10T15:00:00Z");

    private static final CenarioDaConversa CENARIO = CenariosDeTeste.VOO_DO_14_BIS;

    @MockitoBean
    private ProvedorDeIa provedor;

    @Autowired
    private AdaptadorDeIa adaptador;

    @Autowired
    private VerificacaoDoCustoDiario verificacao;

    @Autowired
    private ConsumoDaIa consumo;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    /**
     * O exemplo da revisão: limite de 5,00 e gasto real de 4,50, dos quais 1,00 de sugestão. Pela tarifa
     * do prioritário, a sugestão seria gravada como 2,00, e o dia, como 5,50, com alerta. Pela tarifa do
     * nível dela, o dia fica em 4,50, e não há alerta.
     */
    @Test
    void sugestaoNoNivelPadraoNaoDaAlertaFalso() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("professora@escola.test", null, "ROLE_PROFESSOR"));
        when(provedor.nome()).thenReturn("openai");
        // Tarifas padrão da configuração: fala a 2,00 e 10,00; sugestão a 1,00 e 5,00, por milhão.
        when(provedor.sugerirRoteiro(any())).thenReturn(new Geracao<>(roteiro(), "gpt-6.1-terra", Map.of(),
                new UsoDoModelo(500_000, 100_000)));
        when(provedor.conversar(any(), any())).thenReturn(new Geracao<>("Sou Santos Dumont.", "gpt-6.1-terra",
                Map.of(), new UsoDoModelo(750_000, 200_000)));
        definirLimite("5.00");

        long sugestao = adaptador.sugerirCenario(new PedidoDeSugestao("O voo do 14-bis", null, null, null, null))
                .metadadosId();
        long fala = adaptador.responder(
                new PedidoDeFala(CENARIO, List.of(), "Quem é você?", FinalidadeDoConsumo.FALA_EM_SALA, 1L),
                trecho -> {}).metadadosId();
        levarAoDiaDoTeste(sugestao, fala);

        assertThat(custo(sugestao)).isEqualByComparingTo("1.00");
        assertThat(custo(fala)).isEqualByComparingTo("3.50");
        assertThat(consumo.custoEntre(DIA_DO_TESTE.minusSeconds(12 * 3600), DIA_DO_TESTE.plusSeconds(12 * 3600)).custo())
                .isEqualByComparingTo("4.50");
        assertThat(verificacao.verificar(DIA_DO_TESTE)).isEmpty();
    }

    private void definirLimite(String valor) {
        jdbc.update("""
                INSERT INTO parametro (chave, valor) VALUES ('custo_diario_maximo_usd', ?)
                ON CONFLICT (chave) DO UPDATE SET valor = excluded.valor""", valor);
    }

    private void levarAoDiaDoTeste(long... metadados) {
        for (long id : metadados) {
            jdbc.update("UPDATE resposta_ia_meta SET criado_em = ? WHERE id = ?",
                    OffsetDateTime.ofInstant(DIA_DO_TESTE, ZoneOffset.UTC), id);
        }
    }

    private BigDecimal custo(long metadados) {
        return jdbc.queryForObject("SELECT custo FROM resposta_ia_meta WHERE id = ?", BigDecimal.class, metadados);
    }

    private static RoteiroSugerido roteiro() {
        return new RoteiroSugerido("O voo do 14-bis", "Santos Dumont", null, null, null, null, null, null, null, 20);
    }
}
