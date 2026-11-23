package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.cenarios.apresentacao.CenariosController.ItemDaLista;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoDoCenario;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** O que as listas e a fila mostram de um cenário aprovado com versão nova (RN-08), sem Spring. */
class ItemDaListaTest {

    private static final long AUTOR = 7L;
    private static final Revisor CURADORA = new Revisor(20L, true);
    private static final Instant AGORA = Instant.parse("2026-11-20T13:00:00Z");

    @Test
    void listaMostraAAprovadaEAvisaDaVersaoNovaEmRascunho() {
        Cenario aprovado = aprovado();
        Cenario comNova = aprovado.novaVersao(AUTOR).alterarRascunho(comTitulo("Título novo"));

        ItemDaLista item = ItemDaLista.naLista(comNova, Optional.of(aprovado.versao()));

        assertThat(item.titulo()).isEqualTo("O voo do 14-bis");
        assertThat(item.rotuloDoStatus()).isEqualTo("Aprovado");
        assertThat(item.editavel()).isTrue();
        assertThat(item.aviso()).isEqualTo("Versão 2 em rascunho; a versão 1 continua valendo.");
    }

    /** A devolução de uma versão nova aparece no aviso, e o selo continua o do cenário aprovado. */
    @Test
    void versaoNovaDevolvidaNaoTrocaOSelo() {
        Cenario aprovado = aprovado();
        Cenario devolvido = aprovado.novaVersao(AUTOR).enviarACuradoria(AUTOR).devolver(CURADORA, "Rever", AGORA);

        ItemDaLista item = ItemDaLista.naLista(devolvido, Optional.of(aprovado.versao()));

        assertThat(item.rotuloDoStatus()).isEqualTo("Aprovado");
        assertThat(item.classeDoStatus()).isEqualTo("badge-positive");
        assertThat(item.devolucao()).isEqualTo("Rever");
        assertThat(item.aviso()).isEqualTo("Versão 2 devolvida pela curadoria; a versão 1 continua valendo.");
    }

    @Test
    void filaMostraAVersaoNovaEAvisaQualEstaEmSala() {
        Cenario aprovado = aprovado();
        Cenario enviado = aprovado.novaVersao(AUTOR).alterarRascunho(comTitulo("Título novo")).enviarACuradoria(AUTOR);

        ItemDaLista item = ItemDaLista.naFila(enviado, Optional.of(aprovado.versao()));

        assertThat(item.titulo()).isEqualTo("Título novo");
        assertThat(item.aviso()).isEqualTo("Versão nova de um cenário aprovado: a versão 1 é a que está em sala.");
    }

    @Test
    void semVersaoNovaNaoHaAviso() {
        Cenario aprovado = aprovado();

        assertThat(ItemDaLista.naLista(aprovado, Optional.empty()).aviso()).isNull();
        assertThat(ItemDaLista.naFila(aprovado, Optional.empty()).aviso()).isNull();
    }

    private static Cenario aprovado() {
        var versao = new VersaoDoCenario(2L, 1, StatusDaVersao.RASCUNHO, ConteudosDeTeste.completo(), AUTOR);
        Cenario aprovado = new Cenario(1L, AUTOR, StatusDoCenario.RASCUNHO, versao).enviarACuradoria(AUTOR)
                .aprovar(CURADORA, ConteudosDeTeste.CONFERIDA, AGORA);
        assertThat(aprovado.status()).isEqualTo(StatusDoCenario.APROVADO);
        return aprovado;
    }

    private static ConteudoDoCenario comTitulo(String titulo) {
        var completo = ConteudosDeTeste.completo();
        return new ConteudoDoCenario(titulo, completo.roteiro(), completo.curriculo(), completo.faixaEtaria(),
                completo.duracaoPrevistaMin());
    }
}
