package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste.Situacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Regras da pergunta de teste do curador (RF-09, RN-05, RN-06, RN-36, RNF-13), sem banco nem Spring. */
class PerguntaDeTesteTest {

    private static final long AUTOR = 1;
    private static final long CURADOR = 2;
    private static final long CENARIO = 10;
    private static final long VERSAO = 20;

    private static final PerguntaDeTeste PENDENTE =
            PerguntaDeTeste.reservada(VERSAO, CURADOR, "  Por que voar?  ").comId(5);

    @Test
    void reservaNasceSemRespostaNemMetadados() {
        var reservada = PerguntaDeTeste.reservada(VERSAO, CURADOR, "  Por que voar?  ");

        assertThat(reservada.id()).isNull();
        assertThat(reservada.pergunta()).isEqualTo("Por que voar?");
        assertThat(reservada.situacao()).isEqualTo(Situacao.PENDENTE);
        assertThat(reservada.resposta()).isNull();
        assertThat(reservada.respostaIaMetaId()).isNull();
        assertThat(reservada.rodadaFechada()).isFalse();
        assertThat(reservada.comId(5).id()).isEqualTo(5L);
    }

    @Test
    void concluidaGuardaARespostaEOsMetadadosNaMesmaPergunta() {
        var concluida = PENDENTE.concluida("  Porque sim.  ", 7);

        assertThat(concluida.id()).isEqualTo(5L);
        assertThat(concluida.situacao()).isEqualTo(Situacao.CONCLUIDA);
        assertThat(concluida.resposta()).isEqualTo("Porque sim.");
        assertThat(concluida.respostaIaMetaId()).isEqualTo(7L);
    }

    /** RN-20: a pergunta bloqueada não chega ao modelo, então não tem resposta nem metadados. */
    @Test
    void perguntaBloqueadaFicaSemRespostaESemMetadados() {
        var bloqueada = PENDENTE.comPerguntaBloqueada();

        assertThat(bloqueada.situacao()).isEqualTo(Situacao.PERGUNTA_BLOQUEADA);
        assertThat(bloqueada.resposta()).isNull();
        assertThat(bloqueada.respostaIaMetaId()).isNull();
    }

    /** RN-20: a resposta bloqueada não é guardada, mas o consumo fica nos metadados (RNF-13). */
    @Test
    void respostaBloqueadaNaoGuardaOTextoMasGuardaOsMetadados() {
        var bloqueada = PENDENTE.comRespostaBloqueada(7);

        assertThat(bloqueada.situacao()).isEqualTo(Situacao.RESPOSTA_BLOQUEADA);
        assertThat(bloqueada.resposta()).isNull();
        assertThat(bloqueada.respostaIaMetaId()).isEqualTo(7L);
    }

    /** RNF-13: a falha depois da resposta paga guarda os metadados; antes da resposta, fica sem. */
    @Test
    void falhaGuardaOsMetadadosQuandoHa() {
        assertThat(PENDENTE.comFalha(7L).respostaIaMetaId()).isEqualTo(7L);
        assertThat(PENDENTE.comFalha(null).respostaIaMetaId()).isNull();
        assertThat(PENDENTE.comFalha(7L).situacao()).isEqualTo(Situacao.FALHA);
    }

    /** RN-06: contam só a concluída e a de resposta bloqueada, numa rodada em aberto. */
    @Test
    void soAsPerguntasQueTestaramOPersonagemContam() {
        assertThat(PENDENTE.conta()).isFalse();
        assertThat(PENDENTE.concluida("R", 7).conta()).isTrue();
        assertThat(PENDENTE.comRespostaBloqueada(7).conta()).isTrue();
        assertThat(PENDENTE.comPerguntaBloqueada().conta()).isFalse();
        assertThat(PENDENTE.comFalha(7L).conta()).isFalse();
    }

    /** Uma pergunta de rodada fechada pela devolução não conta, mesmo concluída. */
    @Test
    void perguntaDeRodadaFechadaNaoConta() {
        var fechada = new PerguntaDeTeste(5L, VERSAO, CURADOR, "P", Situacao.CONCLUIDA, "R", 7L, true);

        assertThat(fechada.conta()).isFalse();
    }

    /** O resultado vai uma vez só: uma pergunta com resultado não muda de novo. */
    @Test
    void soAPendenteRecebeResultado() {
        var concluida = PENDENTE.concluida("R", 7);

        assertThatThrownBy(() -> concluida.comFalha(8L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PENDENTE.comPerguntaBloqueada().concluida("R", 7))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void perguntaVaziaERecusada() {
        assertThatThrownBy(() -> PerguntaDeTeste.reservada(VERSAO, CURADOR, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Digite a pergunta de teste");
        assertThatThrownBy(() -> PerguntaDeTeste.reservada(VERSAO, CURADOR, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** O mesmo limite do campo da pergunta na sala, contado depois de tirar os espaços das pontas. */
    @Test
    void perguntaTemNoMaximo1000Caracteres() {
        String noLimite = "a".repeat(PerguntaDeTeste.TAMANHO_MAXIMO_DA_PERGUNTA);

        assertThat(PerguntaDeTeste.textoDaPergunta("  " + noLimite + "  ")).isEqualTo(noLimite);
        assertThatThrownBy(() -> PerguntaDeTeste.textoDaPergunta(noLimite + "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A pergunta de teste tem no máximo 1000 caracteres");
        assertThatThrownBy(() -> PerguntaDeTeste.reservada(VERSAO, CURADOR, noLimite + "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void situacaoECoerenteComARespostaEOsMetadados() {
        assertThatThrownBy(() -> PENDENTE.concluida(" ", 7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerguntaDeTeste(null, VERSAO, CURADOR, "P", Situacao.RESPOSTA_BLOQUEADA, "R", 7L, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerguntaDeTeste(null, VERSAO, CURADOR, "P", Situacao.PERGUNTA_BLOQUEADA, null, 7L, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerguntaDeTeste(null, VERSAO, CURADOR, "P", Situacao.PENDENTE, null, 7L, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerguntaDeTeste(null, VERSAO, CURADOR, "P", null, null, null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void curadorTestaAVersaoEmCuradoriaDeOutroAutor() {
        assertThat(PerguntaDeTeste.versaoTestavel(emCuradoria(AUTOR, AUTOR), CURADOR)).isEqualTo(VERSAO);
    }

    /** RN-05, RN-36: o autor nunca testa a própria versão, mesmo com o perfil Curador. */
    @Test
    void autorDoCenarioNaoTesta() {
        assertThatThrownBy(() -> PerguntaDeTeste.versaoTestavel(emCuradoria(AUTOR, AUTOR), AUTOR))
                .isInstanceOf(TesteNaoPermitido.class)
                .hasMessageContaining("RN-36");
    }

    /** Quem escreveu a versão também não testa, pela mesma regra de quem aprova. */
    @Test
    void quemEscreveuAVersaoNaoTesta() {
        assertThatThrownBy(() -> PerguntaDeTeste.versaoTestavel(emCuradoria(AUTOR, CURADOR), CURADOR))
                .isInstanceOf(TesteNaoPermitido.class);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDaVersao.class, names = "EM_CURADORIA", mode = EnumSource.Mode.EXCLUDE)
    void soAVersaoEmCuradoriaETestada(StatusDaVersao status) {
        var cenario = new Cenario(CENARIO, AUTOR, StatusDoCenario.RASCUNHO,
                new VersaoDoCenario(VERSAO, 1, status, ConteudosDeTeste.completo(), AUTOR));

        assertThatThrownBy(() -> PerguntaDeTeste.versaoTestavel(cenario, CURADOR))
                .isInstanceOf(TesteNaoPermitido.class)
                .hasMessageContaining(status.name());
    }

    @Test
    void rodadaFechadaTemMensagemClara() {
        assertThat(TesteNaoPermitido.rodadaFechada(VERSAO))
                .hasMessageContaining("devolvida")
                .hasMessageContaining("não conta para a aprovação");
    }

    private static Cenario emCuradoria(long autor, long criadaPor) {
        return new Cenario(CENARIO, autor, StatusDoCenario.EM_CURADORIA,
                new VersaoDoCenario(VERSAO, 1, StatusDaVersao.EM_CURADORIA, ConteudosDeTeste.completo(), criadaPor));
    }
}
