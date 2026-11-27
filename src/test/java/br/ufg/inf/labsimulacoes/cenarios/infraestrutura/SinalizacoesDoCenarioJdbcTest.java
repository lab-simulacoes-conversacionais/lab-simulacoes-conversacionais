package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.GravidadeDoErro;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerfilDeQuemSinalizou;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SinalizacaoDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SinalizacoesDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SituacaoDaSinalizacao;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sinalizações recebidas por Cenários no PostgreSQL real (RF-25). O evento pode chegar de novo e a triagem
 * pode chegar antes do registro: as gravações não podem contar a mesma sinalização duas vezes nem desfazer
 * a triagem. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class SinalizacoesDoCenarioJdbcTest {

    private static final Instant REGISTRO = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Instant TRIAGEM = REGISTRO.plus(1, ChronoUnit.HOURS);

    @Autowired
    private SinalizacoesDoCenario sinalizacoes;

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private long autora;
    private long curadora;
    private long cenario;
    private long versao;

    @BeforeEach
    void prepararCenarioAprovado() {
        autora = inserirUsuario("autora-sinalizacao-cenario");
        curadora = inserirUsuario("curadora-sinalizacao-cenario");
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
        Cenario aprovado = aprovar(cenarios.salvar(Cenario.novo(autora, ConteudosDeTeste.completo())));
        cenario = aprovado.id();
        versao = aprovado.versao().id();
    }

    @Test
    void guardaASinalizacaoPendenteComOCenarioDaVersao() {
        assertThat(sinalizacoes.registrar(pendente(1001, GravidadeDoErro.GRAVE))).isTrue();

        assertThat(sinalizacoes.daVersao(versao)).containsExactly(pendente(1001, GravidadeDoErro.GRAVE));
        assertThat(jdbc.queryForObject(
                "SELECT cenario_id FROM sinalizacao_do_cenario WHERE sinalizacao_id = 1001", Long.class))
                .isEqualTo(cenario);
    }

    @Test
    void registroEntregueDeNovoNaoContaDuasVezes() {
        sinalizacoes.registrar(pendente(1001, GravidadeDoErro.MODERADA));

        assertThat(sinalizacoes.registrar(pendente(1001, GravidadeDoErro.MODERADA))).isFalse();
        assertThat(sinalizacoes.daVersao(versao)).hasSize(1);
    }

    @Test
    void triagemEntraNaPendenteSemMudarQuandoFoiRecebida() {
        sinalizacoes.registrar(pendente(1001, GravidadeDoErro.GRAVE));

        assertThat(sinalizacoes.triar(confirmada(1001, GravidadeDoErro.MODERADA, TRIAGEM))).isTrue();

        SinalizacaoDaVersao triada = sinalizacoes.daVersao(versao).getFirst();
        assertThat(triada.situacao()).isEqualTo(SituacaoDaSinalizacao.CONFIRMADA);
        assertThat(triada.gravidadeFinal()).isEqualTo(GravidadeDoErro.MODERADA);
        assertThat(triada.gravidadePercebida()).isEqualTo(GravidadeDoErro.GRAVE);
        assertThat(triada.triadaPorId()).isEqualTo(curadora);
        assertThat(triada.triadaEm()).isEqualTo(TRIAGEM);
        assertThat(triada.recebidaEm()).isEqualTo(REGISTRO);
    }

    @Test
    void descarteFicaSemGravidadeFinal() {
        sinalizacoes.registrar(pendente(1001, GravidadeDoErro.GRAVE));

        sinalizacoes.triar(descartada(1001, TRIAGEM));

        assertThat(sinalizacoes.daVersao(versao)).containsExactly(descartada(1001, REGISTRO));
    }

    /** Na reentrega dos eventos pendentes, a triagem pode chegar antes: vale o momento dela. */
    @Test
    void triagemQueChegaAntesDoRegistroGuardaASinalizacaoComOMomentoDaTriagem() {
        assertThat(sinalizacoes.triar(confirmada(1001, GravidadeDoErro.GRAVE, TRIAGEM))).isTrue();

        assertThat(sinalizacoes.registrar(pendente(1001, GravidadeDoErro.GRAVE))).isFalse();

        SinalizacaoDaVersao triada = sinalizacoes.daVersao(versao).getFirst();
        assertThat(triada.situacao()).isEqualTo(SituacaoDaSinalizacao.CONFIRMADA);
        assertThat(triada.recebidaEm()).isEqualTo(TRIAGEM);
    }

    @Test
    void triagemGravadaNaoETrocadaNemRepetida() {
        sinalizacoes.registrar(pendente(1001, GravidadeDoErro.GRAVE));
        sinalizacoes.triar(confirmada(1001, GravidadeDoErro.GRAVE, TRIAGEM));

        assertThat(sinalizacoes.triar(descartada(1001, TRIAGEM))).isFalse();
        assertThat(sinalizacoes.triar(confirmada(1001, GravidadeDoErro.GRAVE, TRIAGEM))).isFalse();

        SinalizacaoDaVersao triada = sinalizacoes.daVersao(versao).getFirst();
        assertThat(triada.situacao()).isEqualTo(SituacaoDaSinalizacao.CONFIRMADA);
        assertThat(triada.gravidadeFinal()).isEqualTo(GravidadeDoErro.GRAVE);
    }

    @Test
    void listaSoAsDaVersaoNaOrdemEmQueForamFeitas() {
        long outraVersao = aprovar(cenarios.salvar(Cenario.novo(autora, ConteudosDeTeste.completo()))).versao().id();
        sinalizacoes.registrar(pendente(1003, GravidadeDoErro.LEVE));
        sinalizacoes.registrar(pendente(1001, GravidadeDoErro.MODERADA));
        sinalizacoes.registrar(SinalizacaoDaVersao.pendente(
                1002, outraVersao, PerfilDeQuemSinalizou.CURADOR, GravidadeDoErro.GRAVE, REGISTRO));

        assertThat(sinalizacoes.daVersao(versao))
                .extracting(SinalizacaoDaVersao::sinalizacaoId)
                .containsExactly(1001L, 1003L);
        assertThat(sinalizacoes.daVersao(outraVersao))
                .singleElement()
                .extracting(SinalizacaoDaVersao::perfil)
                .isEqualTo(PerfilDeQuemSinalizou.CURADOR);
        assertThat(sinalizacoes.daVersao(-1)).isEmpty();
    }

    @Test
    void achaOCenarioDaVersaoEAVersaoVigente() {
        assertThat(sinalizacoes.cenarioDaVersao(versao)).contains(cenario);
        assertThat(sinalizacoes.versaoVigente(cenario)).contains(versao);
        assertThat(sinalizacoes.cenarioDaVersao(-1)).isEmpty();
        assertThat(sinalizacoes.versaoVigente(-1)).isEmpty();
    }

    @Test
    void cenarioNuncaAprovadoNaoTemVersaoVigente() {
        Cenario rascunho = cenarios.salvar(Cenario.novo(autora, ConteudosDeTeste.completo()));

        assertThat(sinalizacoes.cenarioDaVersao(rascunho.versao().id())).contains(rascunho.id());
        assertThat(sinalizacoes.versaoVigente(rascunho.id())).isEmpty();
    }

    @Test
    void versaoQueNaoExisteERecusada() {
        var semVersao = SinalizacaoDaVersao.pendente(
                1001, -1, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, REGISTRO);

        assertThatThrownBy(() -> sinalizacoes.registrar(semVersao)).isInstanceOf(DataAccessException.class);
    }

    /** O @Repository traduz a IllegalArgumentException para a exceção de acesso a dados do Spring. */
    @Test
    void registroETriagemConferemASituacao() {
        assertThatThrownBy(() -> sinalizacoes.registrar(descartada(1001, TRIAGEM)))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sinalizacoes.triar(pendente(1001, GravidadeDoErro.GRAVE)))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThat(sinalizacoes.daVersao(versao)).isEmpty();
    }

    /**
     * O banco repete a regra da triagem: só a confirmada tem gravidade final, e a triada tem quem e quando.
     * Um caso por execução, porque o primeiro erro aborta a transação do teste no PostgreSQL.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "NULO", value = {
        "CONFIRMADA, NULO,     true,  true",  // confirmada sem gravidade final
        "DESCARTADA, LEVE,     true,  true",  // descartada com gravidade final
        "NULO,       NULO,     true,  false", // pendente com quem triou
        "NULO,       NULO,     false, true",  // pendente com quando triou
        "CONFIRMADA, GRAVE,    false, true",  // triada sem quem triou
        "CONFIRMADA, GRAVE,    true,  false", // triada sem quando triou
        "ADIADA,     NULO,     true,  true",  // decisão desconhecida
        "CONFIRMADA, GRAVISSIMA, true, true"  // gravidade desconhecida
    })
    void bancoRecusaTriagemIncoerenteGravadaPorFora(
            String decisao, String gravidadeFinal, boolean comQuemTriou, boolean comQuandoTriou) {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO sinalizacao_do_cenario (sinalizacao_id, cenario_id, cenario_versao_id, perfil,
                    gravidade_percebida, decisao, gravidade_final, triada_por_id, recebida_em, triada_em)
                VALUES (1001, ?, ?, 'PROFESSOR', 'GRAVE', ?, ?, ?, ?, ?)""",
                cenario,
                versao,
                decisao,
                gravidadeFinal,
                comQuemTriou ? curadora : null,
                REGISTRO.atOffset(ZoneOffset.UTC),
                comQuandoTriou ? TRIAGEM.atOffset(ZoneOffset.UTC) : null))
                .isInstanceOf(DataAccessException.class);
    }

    /** O mesmo INSERT dos casos acima passa quando a triagem é coerente: são os CHECKs que recusam. */
    @Test
    void bancoAceitaTriagemCoerenteGravadaPorFora() {
        jdbc.update("""
                INSERT INTO sinalizacao_do_cenario (sinalizacao_id, cenario_id, cenario_versao_id, perfil,
                    gravidade_percebida, decisao, gravidade_final, triada_por_id, recebida_em, triada_em)
                VALUES (1001, ?, ?, 'PROFESSOR', 'GRAVE', 'CONFIRMADA', 'GRAVE', ?, ?, ?)""",
                cenario,
                versao,
                curadora,
                REGISTRO.atOffset(ZoneOffset.UTC),
                TRIAGEM.atOffset(ZoneOffset.UTC));

        assertThat(sinalizacoes.daVersao(versao)).singleElement()
                .extracting(SinalizacaoDaVersao::situacao)
                .isEqualTo(SituacaoDaSinalizacao.CONFIRMADA);
    }

    private SinalizacaoDaVersao pendente(long id, GravidadeDoErro percebida) {
        return SinalizacaoDaVersao.pendente(id, versao, PerfilDeQuemSinalizou.PROFESSOR, percebida, REGISTRO);
    }

    private SinalizacaoDaVersao confirmada(long id, GravidadeDoErro gravidadeFinal, Instant recebidaEm) {
        return SinalizacaoDaVersao.confirmada(
                id, versao, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, gravidadeFinal, curadora,
                recebidaEm, TRIAGEM);
    }

    private SinalizacaoDaVersao descartada(long id, Instant recebidaEm) {
        return SinalizacaoDaVersao.descartada(
                id, versao, PerfilDeQuemSinalizou.PROFESSOR, GravidadeDoErro.GRAVE, curadora, recebidaEm, TRIAGEM);
    }

    private Cenario aprovar(Cenario rascunho) {
        Cenario enviado = cenarios.salvar(rascunho.enviarACuradoria(autora));
        return cenarios.salvar(enviado.aprovar(new Revisor(curadora, true), ConteudosDeTeste.CONFERIDA, REGISTRO));
    }

    private long inserirUsuario(String prefixo) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id",
                Long.class,
                prefixo + "-" + UUID.randomUUID() + "@escola.test");
    }
}
