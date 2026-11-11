package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Aprovacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Devolucao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoDoCenario;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Aprovação, devolução e versão vigente no PostgreSQL real (RF-08, RN-01). Cada teste desfaz o que gravou. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class CuradoriaNoBancoTest {

    /** O PostgreSQL guarda microssegundos. */
    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private long autor;
    private Revisor curadora;

    @BeforeEach
    void prepararUsuariosECatalogo() {
        autor = inserirUsuario("autora-curadoria@escola.test");
        curadora = new Revisor(inserirUsuario("curadora-curadoria@escola.test"), true);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
    }

    @Test
    void aprovacaoGravaQuemEQuandoETornaAVersaoVigente() {
        Cenario aprovado = cenarios.salvar(emCuradoria().aprovar(curadora, AGORA));

        Cenario lido = cenarios.buscar(aprovado.id()).orElseThrow();
        assertThat(lido.status()).isEqualTo(StatusDoCenario.APROVADO);
        assertThat(lido.versao().status()).isEqualTo(StatusDaVersao.APROVADA);
        assertThat(lido.versao().aprovacao()).isEqualTo(new Aprovacao(curadora.id(), AGORA));
        assertThat(jdbc.queryForObject("SELECT versao_vigente_id FROM cenario WHERE id = ?", Long.class, aprovado.id()))
                .isEqualTo(aprovado.versao().id());
        assertThat(lido.versao().conteudo().curriculo().habilidades()).containsExactly("EF08HI03");
    }

    /**
     * RN-08: o banco recusa alterar a versão aprovada. Salvar o cenário de novo, como fará a suspensão,
     * muda só o cenário.
     */
    @Test
    void salvarCenarioAprovadoNaoTocaAVersaoAprovada() {
        Cenario aprovado = cenarios.salvar(emCuradoria().aprovar(curadora, AGORA));
        var suspenso = new Cenario(aprovado.id(), autor, StatusDoCenario.SUSPENSO, aprovado.versao());

        cenarios.salvar(suspenso);

        assertThat(cenarios.buscar(aprovado.id()).orElseThrow().status()).isEqualTo(StatusDoCenario.SUSPENSO);
        assertThat(cenarios.buscar(aprovado.id()).orElseThrow().versao()).isEqualTo(aprovado.versao());
    }

    /** RN-08: depois de aprovada, a versão não muda nem por SQL direto, e a trigger do banco diz por quê. */
    @Test
    void bancoRecusaAlterarAVersaoAprovada() {
        Cenario aprovado = cenarios.salvar(emCuradoria().aprovar(curadora, AGORA));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE cenario_versao SET titulo = 'Outro título' WHERE id = ?", aprovado.versao().id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("RN-08");
    }

    @Test
    void bancoRecusaApagarAVersaoAprovada() {
        Cenario aprovado = cenarios.salvar(emCuradoria().aprovar(curadora, AGORA));

        assertThatThrownBy(() -> jdbc.update("DELETE FROM cenario_versao WHERE id = ?", aprovado.versao().id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("RN-08");
    }

    @Test
    void devolucaoGravaOComentarioEVoltaARascunho() {
        Cenario devolvido = cenarios.salvar(emCuradoria().devolver(curadora, "Conferir as fontes.", AGORA));

        assertThat(devolvido.ultimaDevolucao().id()).isNotNull();
        Cenario lido = cenarios.buscar(devolvido.id()).orElseThrow();
        assertThat(lido).isEqualTo(devolvido);
        assertThat(lido.status()).isEqualTo(StatusDoCenario.RASCUNHO);
        assertThat(lido.versao().status()).isEqualTo(StatusDaVersao.RASCUNHO);
        assertThat(lido.ultimaDevolucao())
                .isEqualTo(new Devolucao(devolvido.ultimaDevolucao().id(), curadora.id(), "Conferir as fontes.", AGORA));
    }

    /** O autor vê o comentário mais recente, e o histórico continua na tabela. */
    @Test
    void segundaDevolucaoNaoApagaAPrimeira() {
        Cenario primeira = cenarios.salvar(emCuradoria().devolver(curadora, "Primeiro ajuste.", AGORA));
        Cenario reenviado = cenarios.salvar(primeira.enviarACuradoria(autor));

        cenarios.salvar(reenviado.devolver(curadora, "Segundo ajuste.", AGORA.plusSeconds(60)));

        assertThat(cenarios.buscar(primeira.id()).orElseThrow().ultimaDevolucao().comentario()).isEqualTo("Segundo ajuste.");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM devolucao WHERE cenario_versao_id = ?", Integer.class, primeira.versao().id()))
                .isEqualTo(2);
    }

    @Test
    void devolucaoJaGravadaNaoEGravadaDeNovo() {
        Cenario devolvido = cenarios.salvar(emCuradoria().devolver(curadora, "Ajustar.", AGORA));

        cenarios.salvar(devolvido.alterarRascunho(ConteudosDeTeste.completo()));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM devolucao WHERE cenario_versao_id = ?", Integer.class, devolvido.versao().id()))
                .isEqualTo(1);
    }

    @Test
    void versaoVigenteDeCenarioAprovado() {
        Cenario aprovado = cenarios.salvar(emCuradoria().aprovar(curadora, AGORA));

        VersaoDoCenario vigente = cenarios.versaoVigente(aprovado.id()).orElseThrow();

        assertThat(vigente).isEqualTo(aprovado.versao());
    }

    /** RN-01: rascunho e cenário em curadoria não vão para a sala. */
    @Test
    void cenarioNuncaAprovadoNaoTemVersaoVigente() {
        Cenario rascunho = cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo()));
        Cenario enviado = cenarios.salvar(cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo()))
                .enviarACuradoria(autor));

        assertThat(cenarios.versaoVigente(rascunho.id())).isEmpty();
        assertThat(cenarios.versaoVigente(enviado.id())).isEmpty();
        assertThat(cenarios.versaoVigente(-1)).isEmpty();
    }

    /** RN-01: suspenso e arquivado guardam a versão vigente, mas não vão para a sala. */
    @ParameterizedTest
    @ValueSource(strings = {"SUSPENSO", "ARQUIVADO"})
    void cenarioForaDeUsoNaoTemVersaoVigente(String status) {
        Cenario aprovado = cenarios.salvar(emCuradoria().aprovar(curadora, AGORA));
        jdbc.update("UPDATE cenario SET status = ? WHERE id = ?", status, aprovado.id());

        assertThat(cenarios.versaoVigente(aprovado.id())).isEmpty();
    }

    private Cenario emCuradoria() {
        Cenario salvo = cenarios.salvar(Cenario.novo(autor, ConteudosDeTeste.completo()));
        return cenarios.salvar(salvo.enviarACuradoria(autor));
    }

    private long inserirUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
    }
}
