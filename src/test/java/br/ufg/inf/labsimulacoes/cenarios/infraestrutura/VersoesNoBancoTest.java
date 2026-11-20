package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Pagina;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNoHistorico;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Versões novas, histórico e fila da curadoria no PostgreSQL real (RF-11, RN-08). Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class VersoesNoBancoTest {

    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private long autora;
    private Revisor curadora;

    @BeforeEach
    void prepararUsuariosECatalogo() {
        autora = inserirUsuario("autora-versoes@escola.test");
        curadora = new Revisor(inserirUsuario("curadora-versoes@escola.test"), true);
        habilidades.gravar(List.of(
                new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História"),
                new HabilidadeBncc("EF08HI04", "Descrição", "EF", List.of(8), "História")));
    }

    /** RN-08: a versão nova é gravada à parte, e a vigente continua a mesma até a nova ser aprovada. */
    @Test
    void versaoNovaEGravadaSemTrocarAVigente() {
        Cenario aprovado = aprovado();

        Cenario comNova = cenarios.salvar(aprovado.novaVersao(autora));

        assertThat(comNova.versao().id()).isNotNull().isNotEqualTo(aprovado.versao().id());
        Cenario lido = cenarios.buscar(aprovado.id()).orElseThrow();
        assertThat(lido.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(lido.versao().numero()).isEqualTo(2);
        assertThat(lido.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(lido.versao().conteudo()).isEqualTo(aprovado.versao().conteudo());
        assertThat(cenarios.versaoVigente(aprovado.id()).orElseThrow().id()).isEqualTo(aprovado.versao().id());
    }

    @Test
    void aprovarAVersaoNovaTrocaAVigenteEGuardaAAnterior() {
        Cenario aprovado = aprovado();
        Cenario comNova = cenarios.salvar(aprovado.novaVersao(autora));
        Cenario alterado = cenarios.salvar(comNova.alterarRascunho(outroConteudo()));
        Cenario enviado = cenarios.salvar(alterado.enviarACuradoria(autora));

        Cenario aprovadaANova = cenarios.salvar(enviado.aprovar(curadora, AGORA));

        var vigente = cenarios.versaoVigente(aprovado.id()).orElseThrow();
        assertThat(vigente.id()).isEqualTo(aprovadaANova.versao().id());
        assertThat(vigente.conteudo().curriculo().habilidades()).containsExactly("EF08HI03", "EF08HI04");
        assertThat(cenarios.versaoAprovada(aprovado.versao().id()).orElseThrow().versao())
                .isEqualTo(aprovado.versao());
    }

    @Test
    void historicoTrazAsVersoesDaMaisNovaParaAMaisAntigaComAVigenteMarcada() {
        Cenario aprovado = aprovado();
        Cenario comNova = cenarios.salvar(aprovado.novaVersao(autora));

        List<VersaoNoHistorico> historico = cenarios.historico(aprovado.id());

        assertThat(historico).extracting(item -> item.versao().numero()).containsExactly(2, 1);
        assertThat(historico).extracting(VersaoNoHistorico::vigente).containsExactly(false, true);
        assertThat(historico.get(0).versao().id()).isEqualTo(comNova.versao().id());
        assertThat(historico.get(1).versao()).isEqualTo(aprovado.versao());
        assertThat(historico).allSatisfy(item -> assertThat(item.criadaEm()).isNotNull());
        assertThat(cenarios.historico(-1)).isEmpty();
    }

    @Test
    void versaoPeloNumero() {
        Cenario aprovado = aprovado();

        assertThat(cenarios.versao(aprovado.id(), 1)).contains(aprovado.versao());
        assertThat(cenarios.versao(aprovado.id(), 2)).isEmpty();
        assertThat(cenarios.versao(-1, 1)).isEmpty();
    }

    /** RN-08: a fila do curador traz a versão nova do cenário que continua Aprovado. */
    @Test
    void filaDaCuradoriaTrazAVersaoNovaDoCenarioAprovado() {
        Cenario aprovado = aprovado();
        cenarios.salvar(cenarios.salvar(aprovado.novaVersao(autora)).enviarACuradoria(autora));
        long primeiraVez = cenarios.salvar(Cenario.novo(autora, ConteudosDeTeste.completo()).enviarACuradoria(autora))
                .id();
        long soRascunho = cenarios.salvar(Cenario.novo(autora, ConteudosDeTeste.completo())).id();

        var fila = cenarios.listarTodos(StatusDoCenario.EM_CURADORIA, new Pagina(0, 20)).itens();
        var aprovados = cenarios.listarTodos(StatusDoCenario.APROVADO, new Pagina(0, 20)).itens();

        assertThat(fila).extracting(Cenario::id).containsExactlyInAnyOrder(aprovado.id(), primeiraVez)
                .doesNotContain(soRascunho);
        assertThat(fila).allSatisfy(cenario ->
                assertThat(cenario.versao().status()).isEqualTo(StatusDaVersao.EM_CURADORIA));
        assertThat(aprovados).extracting(Cenario::id).contains(aprovado.id());
    }

    private Cenario aprovado() {
        Cenario salvo = cenarios.salvar(Cenario.novo(autora, ConteudosDeTeste.completo()));
        Cenario enviado = cenarios.salvar(salvo.enviarACuradoria(autora));
        return cenarios.salvar(enviado.aprovar(curadora, AGORA));
    }

    private static ConteudoDoCenario outroConteudo() {
        var completo = ConteudosDeTeste.completo();
        return new ConteudoDoCenario("O voo do 14-bis, revisto", completo.roteiro(),
                new VinculoCurricular("História", "EF", 8, List.of("EF08HI03", "EF08HI04")),
                completo.faixaEtaria(), completo.duracaoPrevistaMin());
    }

    private long inserirUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
    }
}
