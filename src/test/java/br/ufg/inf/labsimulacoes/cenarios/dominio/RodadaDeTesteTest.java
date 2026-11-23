package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/** As contagens que a tela do modo de teste mostra (RF-09): a do RN-06 e a do teto (RNF-13). */
class RodadaDeTesteTest {

    private static final long VERSAO = 1;
    private static final long CURADOR = 2;

    @Test
    void rodadaVaziaFaltaOMinimoInteiro() {
        var rodada = new RodadaDeTeste("Santos Dumont", List.of(), 40);

        assertThat(rodada.contam()).isZero();
        assertThat(rodada.minimo()).isEqualTo(PerguntasDeTeste.MINIMO_PARA_APROVAR);
        assertThat(rodada.faltam()).isEqualTo(PerguntasDeTeste.MINIMO_PARA_APROVAR);
        assertThat(rodada.ocupadas()).isZero();
        assertThat(rodada.noTeto()).isFalse();
    }

    /** RN-06: contam a concluída e a de resposta bloqueada; todas ocupam vaga do teto. */
    @Test
    void soAsQueTestaramOPersonagemContamMasTodasOcupamVaga() {
        var rodada = new RodadaDeTeste("Santos Dumont", List.of(
                pergunta(1).concluida("Resposta", 10),
                pergunta(2).comRespostaBloqueada(11),
                pergunta(3).comPerguntaBloqueada(),
                pergunta(4).comFalha(null),
                pergunta(5)), 40);

        assertThat(rodada.contam()).isEqualTo(2);
        assertThat(rodada.faltam()).isEqualTo(3);
        assertThat(rodada.ocupadas()).isEqualTo(5);
    }

    @Test
    void passarDoMinimoNaoDeixaFaltaNegativa() {
        var concluidas = LongStream.rangeClosed(1, 6)
                .mapToObj(id -> pergunta(id).concluida("Resposta", id))
                .toList();

        assertThat(new RodadaDeTeste("Santos Dumont", concluidas, 40).faltam()).isZero();
    }

    @Test
    void rodadaChegaAoTetoComTodasAsVagasOcupadas() {
        var rodada = new RodadaDeTeste("Santos Dumont", List.of(
                pergunta(1).comFalha(null), pergunta(2).comFalha(null), pergunta(3).comFalha(null),
                pergunta(4).comFalha(null), pergunta(5).comFalha(null)), 5);

        assertThat(rodada.noTeto()).isTrue();
        assertThat(rodada.contam()).isZero();
    }

    private static PerguntaDeTeste pergunta(long id) {
        return PerguntaDeTeste.reservada(VERSAO, CURADOR, "Pergunta " + id).comId(id);
    }
}
