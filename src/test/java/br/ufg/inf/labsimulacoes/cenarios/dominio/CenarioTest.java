package br.ufg.inf.labsimulacoes.cenarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CenarioTest {

    private static final long AUTOR = 7L;

    @Test
    void novoCenarioNasceEmRascunhoNaPrimeiraVersao() {
        Cenario cenario = Cenario.novo(AUTOR, ConteudosDeTeste.soComTitulo());

        assertThat(cenario.id()).isNull();
        assertThat(cenario.autorId()).isEqualTo(AUTOR);
        assertThat(cenario.status()).isEqualTo(StatusDoCenario.RASCUNHO);
        assertThat(cenario.versao().numero()).isEqualTo(1);
        assertThat(cenario.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(cenario.versao().criadaPorId()).isEqualTo(AUTOR);
    }

    @Test
    void temAutorComparaOUsuario() {
        Cenario cenario = Cenario.novo(AUTOR, ConteudosDeTeste.soComTitulo());

        assertThat(cenario.temAutor(AUTOR)).isTrue();
        assertThat(cenario.temAutor(8L)).isFalse();
    }

    @Test
    void alterarRascunhoTrocaOConteudoEMantemAVersao() {
        Cenario cenario = Cenario.novo(AUTOR, ConteudosDeTeste.soComTitulo());

        Cenario alterado = cenario.alterarRascunho(ConteudosDeTeste.completo());

        assertThat(alterado.versao().conteudo()).isEqualTo(ConteudosDeTeste.completo());
        assertThat(alterado.versao().numero()).isEqualTo(1);
        assertThat(alterado.status()).isEqualTo(StatusDoCenario.RASCUNHO);
    }

    @Test
    void cenarioCompletoVaiParaACuradoria() {
        Cenario enviado = Cenario.novo(AUTOR, ConteudosDeTeste.completo()).enviarACuradoria();

        assertThat(enviado.status()).isEqualTo(StatusDoCenario.EM_CURADORIA);
        assertThat(enviado.versao().status()).isEqualTo(StatusDaVersao.EM_CURADORIA);
        assertThat(enviado.versao().conteudo()).isEqualTo(ConteudosDeTeste.completo());
    }

    /** RN-02: critério de aceite da issue. */
    @Test
    void naoVaiParaACuradoriaSemHabilidadeNemObjetivo() {
        var r = ConteudosDeTeste.ROTEIRO;
        var c = ConteudosDeTeste.CURRICULO;
        var semObjetivo = new RoteiroDoCenario(r.personagem(), r.papelDaIa(), r.contexto(), List.of(),
                r.limitesDeAssunto(), r.fontes(), null);
        var semHabilidade = new VinculoCurricular(c.componente(), c.etapa(), c.ano(), List.of());
        var conteudo = new ConteudoDoCenario("Título", semObjetivo, semHabilidade, "13 anos", 20);
        Cenario cenario = Cenario.novo(AUTOR, conteudo);

        assertThatThrownBy(cenario::enviarACuradoria)
                .isInstanceOf(CenarioIncompleto.class)
                .hasMessageContaining("habilidade da BNCC")
                .hasMessageContaining("objetivo de aprendizagem")
                .asInstanceOf(InstanceOfAssertFactories.type(CenarioIncompleto.class))
                .extracting(CenarioIncompleto::pendencias, InstanceOfAssertFactories.list(PendenciaDoCenario.class))
                .containsExactly(PendenciaDoCenario.HABILIDADE_BNCC, PendenciaDoCenario.OBJETIVO);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDaVersao.class, names = {"EM_CURADORIA", "APROVADA"})
    void versaoForaDoRascunhoNaoMuda(StatusDaVersao status) {
        var versao = new VersaoDoCenario(2L, 1, status, ConteudosDeTeste.completo(), AUTOR);
        var cenario = new Cenario(1L, AUTOR, StatusDoCenario.EM_CURADORIA, versao);

        assertThatThrownBy(() -> cenario.alterarRascunho(ConteudosDeTeste.soComTitulo()))
                .isInstanceOf(CenarioForaDeRascunho.class)
                .hasMessageContaining(status.name());
        assertThatThrownBy(cenario::enviarACuradoria).isInstanceOf(CenarioForaDeRascunho.class);
    }

    /** RN-08: a nova versão de um cenário aprovado é editável, mesmo com o cenário ainda aprovado. */
    @Test
    void versaoEmRascunhoDeCenarioAprovadoPodeSerAlterada() {
        var versao = new VersaoDoCenario(3L, 2, StatusDaVersao.RASCUNHO, ConteudosDeTeste.soComTitulo(), AUTOR);
        var cenario = new Cenario(1L, AUTOR, StatusDoCenario.APROVADO, versao);

        Cenario alterado = cenario.alterarRascunho(ConteudosDeTeste.completo());

        assertThat(alterado.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(alterado.versao().numero()).isEqualTo(2);
    }
}
