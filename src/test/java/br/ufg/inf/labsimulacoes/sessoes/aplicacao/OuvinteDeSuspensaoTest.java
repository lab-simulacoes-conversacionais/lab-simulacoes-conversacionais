package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.eventos.CenarioSuspenso;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * A falha ao pausar uma sessão não impede a pausa das outras do mesmo cenário (RN-25). Cada pausa tem a própria
 * transação na {@link PausaPorSuspensao}; aqui fica só a ordem e o erro no fim, sem banco.
 */
class OuvinteDeSuspensaoTest {

    private static final CenarioSuspenso SUSPENSAO =
            new CenarioSuspenso(7, 70, 3, "SINALIZACAO_GRAVE", Instant.parse("2026-11-27T13:00:00Z"));

    private final Sessoes sessoes = mock(Sessoes.class);
    private final PausaPorSuspensao pausa = mock(PausaPorSuspensao.class);
    private final OuvinteDeSuspensao ouvinte = new OuvinteDeSuspensao(sessoes, pausa);

    @Test
    void pausaTodasAsSessoesEmAndamentoDoCenario() {
        when(sessoes.emAndamentoDoCenario(7)).thenReturn(List.of(1L, 2L));
        when(pausa.pausar(1)).thenReturn(true);
        when(pausa.pausar(2)).thenReturn(false);

        assertThatCode(() -> ouvinte.cenarioSuspenso(SUSPENSAO)).doesNotThrowAnyException();

        InOrder ordem = inOrder(pausa);
        ordem.verify(pausa).pausar(1);
        ordem.verify(pausa).pausar(2);
    }

    /** As outras pausam; o ouvinte termina com erro, para o evento ficar pendente e ser reprocessado. */
    @Test
    void falhaNumaSessaoNaoImpedeAsOutras() {
        when(sessoes.emAndamentoDoCenario(7)).thenReturn(List.of(1L, 2L, 3L));
        when(pausa.pausar(1)).thenReturn(true);
        when(pausa.pausar(2)).thenThrow(new IllegalStateException("Falha ao gravar a sessão 2"));
        when(pausa.pausar(3)).thenReturn(true);

        assertThatThrownBy(() -> ouvinte.cenarioSuspenso(SUSPENSAO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[2]")
                .hasMessageContaining("cenário 7");

        InOrder ordem = inOrder(pausa);
        ordem.verify(pausa).pausar(1);
        ordem.verify(pausa).pausar(2);
        ordem.verify(pausa).pausar(3);
    }

    @Test
    void cenarioSemSessaoEmAndamentoNaoPausaNada() {
        when(sessoes.emAndamentoDoCenario(7)).thenReturn(List.of());

        assertThatCode(() -> ouvinte.cenarioSuspenso(SUSPENSAO)).doesNotThrowAnyException();
    }
}
