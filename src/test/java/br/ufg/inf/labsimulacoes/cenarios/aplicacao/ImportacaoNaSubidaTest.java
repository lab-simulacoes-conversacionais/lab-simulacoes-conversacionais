package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ImportacaoNaSubidaTest {

    private final ImportacaoDoCatalogo importacao = mock(ImportacaoDoCatalogo.class);

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(ImportacaoDoCatalogo.class, () -> importacao)
            .withUserConfiguration(ImportacaoNaSubida.class);

    @Test
    void importaOCatalogoAoSubir() {
        new ImportacaoNaSubida(importacao).run(new DefaultApplicationArguments());

        verify(importacao).importar();
    }

    @Test
    void ficaLigadaSemAPropriedade() {
        contexto.run(aplicacao -> assertThat(aplicacao).hasSingleBean(ImportacaoNaSubida.class));
    }

    @Test
    void ficaLigadaComAPropriedadeVerdadeira() {
        contexto.withPropertyValues("bncc.importar-ao-iniciar=true")
                .run(aplicacao -> assertThat(aplicacao).hasSingleBean(ImportacaoNaSubida.class));
    }

    @Test
    void propriedadeFalsaDesligaAImportacao() {
        contexto.withPropertyValues("bncc.importar-ao-iniciar=false")
                .run(aplicacao -> assertThat(aplicacao).doesNotHaveBean(ImportacaoNaSubida.class));
    }
}
