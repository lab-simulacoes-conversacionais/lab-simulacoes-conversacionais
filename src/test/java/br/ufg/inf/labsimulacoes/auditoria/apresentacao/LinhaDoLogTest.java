package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Cada ação do log em palavras, com o que ela afetou (RF-26). */
class LinhaDoLogTest {

    /** 14h42 em Brasília. */
    private static final Instant QUANDO = Instant.parse("2026-06-19T13:42:00Z");
    private static final Map<Long, String> NOMES = Map.of(1L, "Rafael Nunes", 2L, "Clara Souza");
    private static final DatasNaTela DATAS = new DatasNaTela(ZoneId.of("America/Sao_Paulo"));

    @Test
    void cenarioAprovadoMostraQuemEQuando() {
        LinhaDoLog linha = linha(AcaoCritica.APROVAR_CENARIO, 1, "12", Map.of("versaoId", 30));

        assertThat(linha.acao()).isEqualTo("Cenário aprovado");
        assertThat(linha.detalhe()).isEqualTo("Cenário nº 12");
        assertThat(linha.usuario()).isEqualTo("Rafael Nunes");
        assertThat(linha.quando()).isEqualTo("19 jun 2026 · 10:42");
        assertThat(linha.destaque()).isFalse();
    }

    @Test
    void suspensaoFicaEmDestaque() {
        LinhaDoLog linha = linha(AcaoCritica.SUSPENDER_CENARIO, 1, "12", Map.of("versaoId", 30, "motivo", "X"));

        assertThat(linha.acao()).isEqualTo("Cenário suspenso");
        assertThat(linha.destaque()).isTrue();
    }

    @Test
    void triagemConfirmadaTrazAGravidadeFinalEOPrazo() {
        LinhaDoLog linha = linha(AcaoCritica.TRIAR_SINALIZACAO, 1, "7",
                Map.of("decisao", "CONFIRMADA", "gravidadeFinal", "GRAVE", "foraDoPrazo", true));

        assertThat(linha.acao()).isEqualTo("Sinalização confirmada");
        assertThat(linha.detalhe()).isEqualTo("Sinalização nº 7 · gravidade final Grave · fora do prazo");
        assertThat(linha.destaque()).isTrue();
    }

    @Test
    void triagemDescartadaNaoTemGravidade() {
        LinhaDoLog linha = linha(AcaoCritica.TRIAR_SINALIZACAO, 1, "7",
                Map.of("decisao", "DESCARTADA", "foraDoPrazo", false));

        assertThat(linha.acao()).isEqualTo("Sinalização descartada");
        assertThat(linha.detalhe()).isEqualTo("Sinalização nº 7");
        assertThat(linha.destaque()).isFalse();
    }

    @Test
    void perfisAlteradosMostramONomeEOsPerfisAntesEDepois() {
        LinhaDoLog linha = linha(AcaoCritica.ALTERAR_PERFIS, 2, "1",
                Map.of("perfisAnteriores", List.of("PROFESSOR"), "perfisNovos", List.of("CURADOR", "PROFESSOR")));

        assertThat(linha.acao()).isEqualTo("Perfis alterados");
        assertThat(linha.detalhe()).isEqualTo("Rafael Nunes: Professor → Curador Pedagógico, Professor");
        assertThat(linha.usuario()).isEqualTo("Clara Souza");
    }

    @Test
    void usuarioSemNomeConhecidoApareceComONumero() {
        LinhaDoLog linha = linha(AcaoCritica.EXPORTAR_RELATORIO, 99, null, Map.of("formato", "CSV", "filtros", "x"));

        assertThat(linha.usuario()).isEqualTo("Usuário nº 99");
        assertThat(linha.detalhe()).isEqualTo("Formato CSV");
        assertThat(linha.acao()).isEqualTo("Relatório exportado");
    }

    @Test
    void todaAcaoTemRotuloNoFiltro() {
        for (AcaoCritica acao : AcaoCritica.values()) {
            assertThat(LinhaDoLog.rotuloDoFiltro(acao)).isNotBlank();
        }
        assertThat(LinhaDoLog.rotuloDoFiltro(AcaoCritica.TRIAR_SINALIZACAO)).isEqualTo("Sinalização triada");
    }

    private static LinhaDoLog linha(AcaoCritica acao, long usuarioId, String entidade, Map<String, ?> detalhes) {
        return LinhaDoLog.de(RegistroDeAuditoria.novo(acao, usuarioId, entidade, detalhes, QUANDO), NOMES, DATAS);
    }
}
