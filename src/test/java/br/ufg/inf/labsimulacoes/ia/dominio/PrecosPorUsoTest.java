package br.ufg.inf.labsimulacoes.ia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** A tarifa de cada uso da IA (RNF-13). */
class PrecosPorUsoTest {

    private static final TabelaDePrecos PRIORITARIO = new TabelaDePrecos(new BigDecimal("2.00"), new BigDecimal("10.00"));
    private static final TabelaDePrecos PADRAO = new TabelaDePrecos(new BigDecimal("1.00"), new BigDecimal("5.00"));
    private static final PrecosPorUso PRECOS = new PrecosPorUso(PRIORITARIO, PADRAO);

    @Test
    void sugestaoTemATarifaPropria() {
        assertThat(PRECOS.para(FinalidadeDoConsumo.SUGESTAO)).isEqualTo(PADRAO);
    }

    /** A fala do personagem, na sala e no teste do curador, roda com a configuração da conversa. */
    @Test
    void falaETesteDoCuradorTemATarifaDaConversa() {
        assertThat(PRECOS.para(FinalidadeDoConsumo.FALA_EM_SALA)).isEqualTo(PRIORITARIO);
        assertThat(PRECOS.para(FinalidadeDoConsumo.TESTE_DO_CURADOR)).isEqualTo(PRIORITARIO);
    }

    /** Só a fala pode vir sem finalidade, pelo construtor antigo do pedido. */
    @Test
    void semFinalidadeEFala() {
        assertThat(PRECOS.para(null)).isEqualTo(PRIORITARIO);
    }

    @Test
    void asDuasTabelasSaoObrigatorias() {
        assertThatThrownBy(() -> new PrecosPorUso(null, PADRAO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PrecosPorUso(PRIORITARIO, null)).isInstanceOf(NullPointerException.class);
    }
}
