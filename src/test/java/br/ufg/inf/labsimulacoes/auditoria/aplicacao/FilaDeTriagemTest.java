package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.FilaDeTriagem.ItemDaFila;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.FilaDeTriagem.PendenciaForaDoPrazo;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TipoDeErro;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * A fila da triagem com o destaque das pendências fora do prazo de 2 dias úteis (RF-24, RN-24), para o
 * curador e, sem os textos, para o administrador. O relógio é fixo numa quinta-feira; cada teste desfaz o
 * que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class FilaDeTriagemTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    /** Quinta-feira, 26 de novembro de 2026, às 10h na escola. */
    private static final Instant AGORA = naEscola("2026-11-26T10:00");

    @MockitoBean
    private RelogioDaAuditoria relogio;

    @Autowired
    private FilaDeTriagem fila;

    @Autowired
    private JdbcTemplate jdbc;

    private SinalizacoesNoBanco banco;
    /** Segunda às 9h: venceu na quarta às 9h. */
    private long atrasada;
    /** Segunda às 10h: vence na quarta às 10h, um dia antes de agora. */
    private long outraAtrasada;
    /** Terça às 10h: vence agora, no limite incluído. */
    private long noLimite;
    /** Quarta: vence na sexta. */
    private long noPrazo;

    @BeforeEach
    void prepararFila() {
        when(relogio.agora()).thenReturn(AGORA);
        banco = new SinalizacoesNoBanco(jdbc);
        noPrazo = banco.pendente(naEscola("2026-11-25T14:00"));
        atrasada = banco.pendente(naEscola("2026-11-23T09:00"));
        noLimite = banco.pendente(naEscola("2026-11-24T10:00"));
        outraAtrasada = banco.pendente(naEscola("2026-11-23T10:00"));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void curadorVeAFilaDaMaisAntigaComODestaqueDoPrazo() {
        entrar("ROLE_CURADOR");

        List<ItemDaFila> daVersao = daVersao(fila.pendentes());

        assertThat(daVersao).extracting(item -> item.sinalizacao().id())
                .containsExactly(atrasada, outraAtrasada, noLimite, noPrazo);
        Map<Long, ItemDaFila> porId = daVersao.stream()
                .collect(Collectors.toMap(item -> item.sinalizacao().id(), Function.identity()));
        assertThat(porId.get(atrasada).foraDoPrazo()).isTrue();
        assertThat(porId.get(atrasada).limite()).isEqualTo(naEscola("2026-11-25T09:00"));
        assertThat(porId.get(outraAtrasada).foraDoPrazo()).isTrue();
        assertThat(porId.get(noLimite).foraDoPrazo()).isFalse();
        assertThat(porId.get(noPrazo).foraDoPrazo()).isFalse();
        assertThat(porId.get(noPrazo).limite()).isEqualTo(naEscola("2026-11-27T14:00"));
        assertThat(porId.get(noPrazo).sinalizacao().justificativa()).isEqualTo("O voo homologado foi em 1906.");
    }

    @Test
    void sinalizacaoTriadaSaiDaFila() {
        jdbc.update("UPDATE sinalizacao SET status = 'DESCARTADA', triada_por_id = ?, triada_em = now() WHERE id = ?",
                banco.professorId, atrasada);
        entrar("ROLE_CURADOR");

        assertThat(daVersao(fila.pendentes())).extracting(item -> item.sinalizacao().id())
                .containsExactly(outraAtrasada, noLimite, noPrazo);
    }

    /** RN-24: o administrador vê as pendências atrasadas, sem a pergunta, a resposta e a justificativa. */
    @Test
    void administradorVeAsPendenciasForaDoPrazo() {
        entrar("ROLE_ADMINISTRADOR");

        var resumo = fila.resumoDoPrazo();

        List<PendenciaForaDoPrazo> daVersao = resumo.foraDoPrazo().stream()
                .filter(pendencia -> List.of(atrasada, outraAtrasada, noLimite, noPrazo)
                        .contains(pendencia.sinalizacaoId()))
                .toList();
        assertThat(daVersao).containsExactly(
                new PendenciaForaDoPrazo(atrasada, TipoDeErro.ERRO_HISTORICO, naEscola("2026-11-23T09:00"),
                        naEscola("2026-11-25T09:00")),
                new PendenciaForaDoPrazo(outraAtrasada, TipoDeErro.ERRO_HISTORICO, naEscola("2026-11-23T10:00"),
                        naEscola("2026-11-25T10:00")));
        assertThat(resumo.temAtrasadas()).isTrue();
        assertThat(resumo.pendentes()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void curadorTambemVeOResumo() {
        entrar("ROLE_CURADOR");

        assertThat(fila.resumoDoPrazo().foraDoPrazo())
                .extracting(PendenciaForaDoPrazo::sinalizacaoId)
                .contains(atrasada, outraAtrasada)
                .doesNotContain(noLimite, noPrazo);
    }

    /** O administrador não vê o conteúdo pedagógico das sinalizações (RN-37). */
    @Test
    void administradorNaoVeAFilaComOsTextos() {
        entrar("ROLE_ADMINISTRADOR");

        assertThatThrownBy(() -> fila.pendentes()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void professorNaoVeAFilaNemOResumo() {
        entrar("ROLE_PROFESSOR");

        assertThatThrownBy(() -> fila.pendentes()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fila.resumoDoPrazo()).isInstanceOf(AccessDeniedException.class);
    }

    private List<ItemDaFila> daVersao(List<ItemDaFila> itens) {
        return itens.stream().filter(item -> item.sinalizacao().versaoId() == banco.versaoId).toList();
    }

    private static void entrar(String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("pessoa@escola.test", null, perfis));
    }

    private static Instant naEscola(String momento) {
        return LocalDateTime.parse(momento).atZone(FUSO).toInstant();
    }
}
