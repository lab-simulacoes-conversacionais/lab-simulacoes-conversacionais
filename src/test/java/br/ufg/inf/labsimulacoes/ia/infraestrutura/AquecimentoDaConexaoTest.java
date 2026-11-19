package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AquecimentoDaConexaoTest {

    private final ProvedorDeIa provedor = mock(ProvedorDeIa.class);
    private final AquecimentoDaConexao aquecimento = new AquecimentoDaConexao(provedor);

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(ProvedorDeIa.class, () -> provedor)
            .withUserConfiguration(AquecimentoDaConexao.class);

    @Test
    void aquecerFazUmaModeracao() {
        when(provedor.moderar(anyString())).thenReturn(new ResultadoDaModeracao(false, List.of()));

        aquecimento.aquecer();

        verify(provedor).moderar(AquecimentoDaConexao.TEXTO);
    }

    @Test
    void falhaDoProvedorNaoSaiDoAquecimento() {
        when(provedor.moderar(anyString())).thenThrow(new FalhaNoProvedorDeIa("fora do ar", null));

        assertThatCode(aquecimento::aquecer).doesNotThrowAnyException();
    }

    @Test
    void outroErroNaoSaiDoAquecimento() {
        when(provedor.moderar(anyString())).thenThrow(new IllegalStateException("http://provedor"));

        assertThatCode(aquecimento::aquecer).doesNotThrowAnyException();
    }

    @Test
    void aoSubirAqueceNumaThreadAParte() {
        when(provedor.moderar(anyString())).thenReturn(new ResultadoDaModeracao(false, List.of()));

        aquecimento.aoSubir();

        verify(provedor, timeout(5_000)).moderar(AquecimentoDaConexao.TEXTO);
    }

    @Test
    void ligadoSoComAPropriedade() {
        contexto.run(semPropriedade -> assertThat(semPropriedade).doesNotHaveBean(AquecimentoDaConexao.class));
        contexto.withPropertyValues("ia.aquecimento.habilitado=false")
                .run(desligado -> assertThat(desligado).doesNotHaveBean(AquecimentoDaConexao.class));
        contexto.withPropertyValues("ia.aquecimento.habilitado=true")
                .run(ligado -> assertThat(ligado).hasSingleBean(AquecimentoDaConexao.class));
    }
}
