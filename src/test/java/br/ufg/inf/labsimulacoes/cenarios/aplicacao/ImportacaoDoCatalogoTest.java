package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.cenarios.dominio.BuscasDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CatalogoInvalido;
import br.ufg.inf.labsimulacoes.cenarios.dominio.FonteDeHabilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.LeituraDeHabilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ResultadoDaGravacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ResultadoDaImportacao;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ImportacaoDoCatalogoTest {

    private static final HabilidadeBncc HISTORIA = new HabilidadeBncc("EF08HI03", "Texto.", "EF", List.of(8), "História");
    private static final HabilidadeBncc PORTUGUES =
            new HabilidadeBncc("EF67LP08", "Texto.", "EF", List.of(6, 7), "Língua Portuguesa");
    private static final HabilidadeBncc MATEMATICA = new HabilidadeBncc("EM13MAT101", "Texto.", "EM", List.of(1), "Matemática");

    private final Habilidades habilidades = mock(Habilidades.class);
    private final BuscasDoCatalogo buscas = mock(BuscasDoCatalogo.class);

    @AfterEach
    void encerrarTransacaoSimulada() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void contaInseridasAtualizadasInalteradasEIgnoradas() {
        when(habilidades.gravar(anyList())).thenReturn(new ResultadoDaGravacao(1, 1));
        var importacao = importacao(fonte("EF", 2, HISTORIA, PORTUGUES), fonte("EM", 1, MATEMATICA));

        ResultadoDaImportacao resultado = importacao.importar();

        assertThat(resultado).isEqualTo(new ResultadoDaImportacao(1, 1, 1, 3));
        verify(habilidades).gravar(List.of(HISTORIA, PORTUGUES, MATEMATICA));
    }

    /** Uma etapa nova é só mais uma fonte: as habilidades de todas vão juntas para a gravação. */
    @Test
    void juntaAsHabilidadesDeTodasAsFontes() {
        when(habilidades.gravar(anyList())).thenReturn(new ResultadoDaGravacao(3, 0));

        importacao(fonte("EF", 0, HISTORIA, PORTUGUES), fonte("EM", 0, MATEMATICA)).importar();

        verify(habilidades).gravar(List.of(HISTORIA, PORTUGUES, MATEMATICA));
    }

    @Test
    void codigoRepetidoEntreAsFontesFalhaSemGravar() {
        var importacao = importacao(fonte("EF", 0, HISTORIA), fonte("EF", 0, HISTORIA));

        assertThatThrownBy(importacao::importar)
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("EF08HI03")
                .hasMessageContaining("mais de uma vez");
        verify(habilidades, never()).gravar(any());
        verify(buscas, never()).esquecerTudo();
    }

    @Test
    void falhaNaLeituraNaoGravaNemLimpaOCache() {
        FonteDeHabilidades quebrada = () -> {
            throw new CatalogoInvalido("arquivo com problema");
        };

        assertThatThrownBy(() -> importacao(fonte("EF", 0, HISTORIA), quebrada).importar())
                .isInstanceOf(CatalogoInvalido.class);
        verify(habilidades, never()).gravar(any());
        verify(buscas, never()).esquecerTudo();
    }

    @Test
    void foraDeTransacaoLimpaOCacheLogoDepoisDeGravar() {
        when(habilidades.gravar(anyList())).thenReturn(new ResultadoDaGravacao(1, 0));

        importacao(fonte("EF", 0, HISTORIA)).importar();

        verify(buscas).esquecerTudo();
    }

    @Test
    void dentroDeTransacaoSoLimpaOCacheDepoisDaConfirmacao() {
        when(habilidades.gravar(anyList())).thenReturn(new ResultadoDaGravacao(1, 0));
        TransactionSynchronizationManager.initSynchronization();

        importacao(fonte("EF", 0, HISTORIA)).importar();

        verify(buscas, never()).esquecerTudo();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(buscas).esquecerTudo();
    }

    private ImportacaoDoCatalogo importacao(FonteDeHabilidades... fontes) {
        return new ImportacaoDoCatalogo(List.of(fontes), habilidades, buscas);
    }

    private static FonteDeHabilidades fonte(String etapa, int ignoradas, HabilidadeBncc... lidas) {
        return () -> new LeituraDeHabilidades(etapa, List.of(lidas), ignoradas);
    }
}
