package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.cenarios.apresentacao.HistoricoExibido.VersaoExibida;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNoHistorico;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** O histórico de versões como a tela mostra (RF-11, RN-08), sem Spring. */
class HistoricoExibidoTest {

    private static final long AUTOR = 7L;
    private static final Revisor CURADORA = new Revisor(20L, true);
    private static final Instant CRIADA = Instant.parse("2026-11-20T13:05:00Z");

    @Test
    void cenarioDeUmaVersaoNaoTemComparacao() {
        Cenario rascunho = new Cenario(1L, AUTOR, StatusDoCenario.RASCUNHO,
                new VersaoDoCenario(1L, 1, StatusDaVersao.RASCUNHO, ConteudosDeTeste.completo(), AUTOR));

        var historico = HistoricoExibido.de(List.of(noHistorico(rascunho.versao(), false)), rascunho, true);

        assertThat(historico.temComparacao()).isFalse();
        assertThat(historico.podeCriarVersao()).isFalse();
        assertThat(historico.versoes()).singleElement().satisfies(v -> {
            assertThat(v.rotulo()).isEqualTo("Rascunho");
            // No horário da escola, e não no do servidor.
            assertThat(v.criadaEm()).isEqualTo("20/11/2026 às 10:05");
            assertThat(v.restauravel()).isFalse();
        });
    }

    /** Sem escolha, a comparação mostra a versão mais nova e a última aprovada antes dela. */
    @Test
    void comparaAVersaoMaisNovaComAUltimaAprovada() {
        Cenario aprovado = aprovado();
        Cenario comNova = aprovado.novaVersao(AUTOR);
        var historico = HistoricoExibido.de(
                List.of(noHistorico(comNova.versao(), false), noHistorico(aprovado.versao(), true)), comNova, true);

        assertThat(historico.novaPadrao()).isEqualTo(2);
        assertThat(historico.antigaPadrao()).isEqualTo(1);
        assertThat(historico.versoes()).extracting(VersaoExibida::rotulo).containsExactly("Rascunho", "Vigente");
        // Já há uma versão em rascunho: nada de outra versão nova nem de restaurar (RN-08).
        assertThat(historico.podeCriarVersao()).isFalse();
        assertThat(historico.versoes()).noneMatch(VersaoExibida::restauravel);
    }

    @Test
    void autorDeCenarioAprovadoCriaVersaoERestauraAsAprovadasAnteriores() {
        var v1 = new VersaoDoCenario(1L, 1, StatusDaVersao.APROVADA, ConteudosDeTeste.completo(), AUTOR);
        var v2 = new VersaoDoCenario(2L, 2, StatusDaVersao.APROVADA, ConteudosDeTeste.completo(), AUTOR);
        Cenario aprovado = new Cenario(1L, AUTOR, StatusDoCenario.APROVADO, v2);
        var linhas = List.of(noHistorico(v2, true), noHistorico(v1, false));

        var doAutor = HistoricoExibido.de(linhas, aprovado, true);
        var deOutro = HistoricoExibido.de(linhas, aprovado, false);

        assertThat(doAutor.podeCriarVersao()).isTrue();
        assertThat(doAutor.versoes()).extracting(VersaoExibida::restauravel).containsExactly(false, true);
        assertThat(doAutor.versoes()).extracting(VersaoExibida::rotulo).containsExactly("Vigente", "Aprovada");
        assertThat(deOutro.podeCriarVersao()).isFalse();
        assertThat(deOutro.versoes()).noneMatch(VersaoExibida::restauravel);
    }

    private static Cenario aprovado() {
        var versao = new VersaoDoCenario(2L, 1, StatusDaVersao.RASCUNHO, ConteudosDeTeste.completo(), AUTOR);
        return new Cenario(1L, AUTOR, StatusDoCenario.RASCUNHO, versao).enviarACuradoria(AUTOR)
                .aprovar(CURADORA, ConteudosDeTeste.CONFERIDA, CRIADA);
    }

    private static VersaoNoHistorico noHistorico(VersaoDoCenario versao, boolean vigente) {
        return new VersaoNoHistorico(versao, CRIADA, vigente);
    }
}
