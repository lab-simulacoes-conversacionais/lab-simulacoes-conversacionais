package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaginaDoLogTest {

    private static final Instant AGORA = Instant.parse("2026-11-08T13:45:10Z");

    @Test
    void linhaAMaisIndicaQueHaProximaPagina() {
        List<RegistroDeAuditoria> lidas = linhas(3);

        var pagina = PaginaDoLog.lidaComUmaAMais(lidas, 2);

        assertThat(pagina.linhas()).containsExactly(lidas.get(0), lidas.get(1));
        assertThat(pagina.temProxima()).isTrue();
        assertThat(pagina.cursorDaProxima()).contains(CursorDoLog.apos(lidas.get(1)));
    }

    @Test
    void exatamenteOTamanhoEAUltimaPagina() {
        List<RegistroDeAuditoria> lidas = linhas(2);

        var pagina = PaginaDoLog.lidaComUmaAMais(lidas, 2);

        assertThat(pagina.linhas()).isEqualTo(lidas);
        assertThat(pagina.temProxima()).isFalse();
        assertThat(pagina.cursorDaProxima()).isEmpty();
    }

    @Test
    void menosQueOTamanhoEAUltimaPagina() {
        assertThat(PaginaDoLog.lidaComUmaAMais(linhas(1), 2).temProxima()).isFalse();
        assertThat(PaginaDoLog.lidaComUmaAMais(List.of(), 2).linhas()).isEmpty();
    }

    @Test
    void linhasDaPaginaNaoMudamDepoisDeMontada() {
        List<RegistroDeAuditoria> lidas = new ArrayList<>(linhas(1));
        var pagina = PaginaDoLog.lidaComUmaAMais(lidas, 2);

        lidas.clear();

        assertThat(pagina.linhas()).hasSize(1);
    }

    private static List<RegistroDeAuditoria> linhas(int quantas) {
        var linhas = new ArrayList<RegistroDeAuditoria>();
        for (int i = 0; i < quantas; i++) {
            linhas.add(new RegistroDeAuditoria(
                    (long) quantas - i, 7, AcaoCritica.APROVAR_CENARIO, "1", Map.of(), AGORA.minusSeconds(i), "h"));
        }
        return linhas;
    }
}
