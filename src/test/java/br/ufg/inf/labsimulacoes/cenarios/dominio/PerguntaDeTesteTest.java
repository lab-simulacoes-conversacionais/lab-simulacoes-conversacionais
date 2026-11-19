package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntaDeTeste.Desfecho;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Regras da pergunta de teste do curador (RF-09, RN-05, RN-06, RN-36), sem banco nem Spring. */
class PerguntaDeTesteTest {

    private static final long AUTOR = 1;
    private static final long CURADOR = 2;
    private static final long CENARIO = 10;
    private static final long VERSAO = 20;

    @Test
    void respostaExibidaGuardaOTextoEOsMetadados() {
        var pergunta = PerguntaDeTeste.exibida(VERSAO, CURADOR, "  Por que voar?  ", "  Porque sim.  ", 7);

        assertThat(pergunta.pergunta()).isEqualTo("Por que voar?");
        assertThat(pergunta.resposta()).isEqualTo("Porque sim.");
        assertThat(pergunta.desfecho()).isEqualTo(Desfecho.EXIBIDA);
        assertThat(pergunta.respostaIaMetaId()).isEqualTo(7L);
        assertThat(pergunta.id()).isNull();
        assertThat(pergunta.comId(5).id()).isEqualTo(5L);
    }

    /** RN-20: a pergunta bloqueada não chega ao modelo, então não tem resposta nem metadados. */
    @Test
    void perguntaBloqueadaFicaSemRespostaESemMetadados() {
        var pergunta = PerguntaDeTeste.comPerguntaBloqueada(VERSAO, CURADOR, "Texto impróprio");

        assertThat(pergunta.desfecho()).isEqualTo(Desfecho.PERGUNTA_BLOQUEADA);
        assertThat(pergunta.resposta()).isNull();
        assertThat(pergunta.respostaIaMetaId()).isNull();
    }

    /** RN-20: a resposta bloqueada não é guardada, mas o consumo fica nos metadados (RNF-13). */
    @Test
    void respostaBloqueadaNaoGuardaOTextoMasGuardaOsMetadados() {
        var pergunta = PerguntaDeTeste.comRespostaBloqueada(VERSAO, CURADOR, "Pergunta", 7);

        assertThat(pergunta.desfecho()).isEqualTo(Desfecho.RESPOSTA_BLOQUEADA);
        assertThat(pergunta.resposta()).isNull();
        assertThat(pergunta.respostaIaMetaId()).isEqualTo(7L);
    }

    /** RN-06: a pergunta bloqueada não conta para aprovar; a resposta bloqueada conta. */
    @Test
    void soAPerguntaBloqueadaNaoConta() {
        assertThat(PerguntaDeTeste.exibida(VERSAO, CURADOR, "P", "R", 7).conta()).isTrue();
        assertThat(PerguntaDeTeste.comRespostaBloqueada(VERSAO, CURADOR, "P", 7).conta()).isTrue();
        assertThat(PerguntaDeTeste.comPerguntaBloqueada(VERSAO, CURADOR, "P").conta()).isFalse();
    }

    @Test
    void perguntaVaziaERecusada() {
        assertThatThrownBy(() -> PerguntaDeTeste.comPerguntaBloqueada(VERSAO, CURADOR, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PerguntaDeTeste.comPerguntaBloqueada(VERSAO, CURADOR, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void desfechoECoerenteComAResposta() {
        assertThatThrownBy(() -> PerguntaDeTeste.exibida(VERSAO, CURADOR, "P", " ", 7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerguntaDeTeste(null, VERSAO, CURADOR, "P", Desfecho.RESPOSTA_BLOQUEADA, "R", 7L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerguntaDeTeste(null, VERSAO, CURADOR, "P", Desfecho.PERGUNTA_BLOQUEADA, null, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerguntaDeTeste(null, VERSAO, CURADOR, "P", null, null, null))
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
    void aVersaoQueContinuaIgualAindaETestavel() {
        assertThatCode(() -> PerguntaDeTeste.exigirAindaTestavel(
                emCuradoria(AUTOR, AUTOR), CURADOR, VERSAO, ConteudosDeTeste.completo()))
                .doesNotThrowAnyException();
    }

    /** A devolução volta a mesma versão a rascunho: depois de editada e reenviada, o id é o mesmo. */
    @Test
    void aVersaoComOutroConteudoNaoEMaisAQueFoiTestada() {
        var outro = new ConteudoDoCenario(
                "Outro título", ConteudosDeTeste.ROTEIRO, ConteudosDeTeste.CURRICULO, "13 a 14 anos", 20);

        assertThatThrownBy(() -> PerguntaDeTeste.exigirAindaTestavel(emCuradoria(AUTOR, AUTOR), CURADOR, VERSAO, outro))
                .isInstanceOf(TesteNaoPermitido.class)
                .hasMessageContaining("mudou");
    }

    @Test
    void outraVersaoNaoEMaisAQueFoiTestada() {
        assertThatThrownBy(() -> PerguntaDeTeste.exigirAindaTestavel(
                emCuradoria(AUTOR, AUTOR), CURADOR, VERSAO + 1, ConteudosDeTeste.completo()))
                .isInstanceOf(TesteNaoPermitido.class);
    }

    @Test
    void versaoQueSaiuDaCuradoriaNaoEMaisTestavel() {
        var devolvido = new Cenario(CENARIO, AUTOR, StatusDoCenario.RASCUNHO,
                new VersaoDoCenario(VERSAO, 1, StatusDaVersao.RASCUNHO, ConteudosDeTeste.completo(), AUTOR));

        assertThatThrownBy(() -> PerguntaDeTeste.exigirAindaTestavel(devolvido, CURADOR, VERSAO, ConteudosDeTeste.completo()))
                .isInstanceOf(TesteNaoPermitido.class);
    }

    private static Cenario emCuradoria(long autor, long criadaPor) {
        return new Cenario(CENARIO, autor, StatusDoCenario.EM_CURADORIA,
                new VersaoDoCenario(VERSAO, 1, StatusDaVersao.EM_CURADORIA, ConteudosDeTeste.completo(), criadaPor));
    }
}
