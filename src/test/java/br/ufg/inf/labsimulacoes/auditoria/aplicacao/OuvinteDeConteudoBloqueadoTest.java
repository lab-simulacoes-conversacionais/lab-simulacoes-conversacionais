package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.ufg.inf.labsimulacoes.auditoria.dominio.CasoBloqueado;
import br.ufg.inf.labsimulacoes.auditoria.dominio.CasosBloqueados;
import br.ufg.inf.labsimulacoes.eventos.ConteudoBloqueado;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tradução do evento para o caso da fila do curador (RN-20), sem banco. */
class OuvinteDeConteudoBloqueadoTest {

    private static final Instant MOMENTO = Instant.parse("2026-11-11T13:45:10Z");

    private final CasosBloqueados casos = mock(CasosBloqueados.class);
    private final OuvinteDeConteudoBloqueado ouvinte = new OuvinteDeConteudoBloqueado(casos);

    @Test
    void respostaBloqueadaAbreOCasoComTudoOQueATriagemUsa() {
        ouvinte.conteudoBloqueado(new ConteudoBloqueado(
                7, 30, ConteudoBloqueado.Origem.RESPOSTA, "Quem é você?", "Frase bloqueada.", List.of("violence"), 99L,
                MOMENTO));

        assertThat(aberto()).isEqualTo(new CasoBloqueado(
                7, 30, ConteudoBloqueado.Origem.RESPOSTA, "Quem é você?", "Frase bloqueada.", List.of("violence"), 99L,
                MOMENTO));
    }

    @Test
    void perguntaBloqueadaAbreOCasoSemTrechoNemMetadados() {
        ouvinte.conteudoBloqueado(new ConteudoBloqueado(
                7, 30, ConteudoBloqueado.Origem.PERGUNTA, "Pergunta imprópria", null, List.of(), null, MOMENTO));

        CasoBloqueado caso = aberto();
        assertThat(caso.origem()).isEqualTo(ConteudoBloqueado.Origem.PERGUNTA);
        assertThat(caso.trechoBloqueado()).isNull();
        assertThat(caso.respostaIaMetaId()).isNull();
        assertThat(caso.categorias()).isEmpty();
    }

    private CasoBloqueado aberto() {
        ArgumentCaptor<CasoBloqueado> caso = ArgumentCaptor.forClass(CasoBloqueado.class);
        verify(casos).abrir(caso.capture());
        return caso.getValue();
    }
}
