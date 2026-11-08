package br.ufg.inf.labsimulacoes.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CadeiaDeAuditoriaTest {

    private static final Instant AGORA = Instant.parse("2026-11-08T13:45:10Z");

    @Test
    void logVazioEstaIntegro() {
        assertThat(CadeiaDeAuditoria.primeiraAdulterada(List.of())).isEmpty();
    }

    @Test
    void cadeiaGravadaPeloSistemaEstaIntegra() {
        assertThat(CadeiaDeAuditoria.primeiraAdulterada(cadeia(4))).isEmpty();
    }

    @Test
    void linhaAlteradaEApontada() {
        List<RegistroDeAuditoria> linhas = cadeia(4);
        RegistroDeAuditoria original = linhas.get(2);
        var alterada = new RegistroDeAuditoria(original.id(), 99, original.acao(), original.entidadeId(),
                original.detalhes(), original.ocorridoEm(), original.hash());
        linhas.set(2, alterada);

        assertThat(CadeiaDeAuditoria.primeiraAdulterada(linhas)).contains(alterada);
    }

    @Test
    void linhaApagadaNoMeioQuebraASeguinte() {
        List<RegistroDeAuditoria> linhas = cadeia(4);
        linhas.remove(1);

        assertThat(CadeiaDeAuditoria.primeiraAdulterada(linhas)).contains(linhas.get(1));
    }

    @Test
    void primeiraLinhaApagadaQuebraANovaPrimeira() {
        List<RegistroDeAuditoria> linhas = cadeia(3);
        linhas.remove(0);

        assertThat(CadeiaDeAuditoria.primeiraAdulterada(linhas)).contains(linhas.get(0));
    }

    @Test
    void linhaInseridaSemPassarPeloSistemaEApontada() {
        List<RegistroDeAuditoria> linhas = cadeia(3);
        var semHash = registro(10);
        linhas.add(semHash);

        assertThat(CadeiaDeAuditoria.primeiraAdulterada(linhas)).contains(semHash);
    }

    private static List<RegistroDeAuditoria> cadeia(int tamanho) {
        var linhas = new ArrayList<RegistroDeAuditoria>();
        String anterior = null;
        for (long id = 1; id <= tamanho; id++) {
            RegistroDeAuditoria linha = registro(id).encadeadoApos(anterior);
            linhas.add(linha);
            anterior = linha.hash();
        }
        return linhas;
    }

    private static RegistroDeAuditoria registro(long id) {
        return new RegistroDeAuditoria(
                id, 7, AcaoCritica.APROVAR_CENARIO, String.valueOf(id), Map.of("versaoId", id), AGORA, null);
    }
}
