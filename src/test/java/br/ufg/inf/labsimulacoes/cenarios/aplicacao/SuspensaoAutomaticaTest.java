package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.TesteDoPersonagemFeito;
import br.ufg.inf.labsimulacoes.cenarios.dominio.AprovacaoIncompleta;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Revisor;
import br.ufg.inf.labsimulacoes.eventos.SinalizacaoRegistrada;
import br.ufg.inf.labsimulacoes.eventos.SinalizacaoTriada;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Critério de aceite da RF-25: cada regra do RN-23 de ponta a ponta. Os eventos da Auditoria são publicados
 * como ela publica; o ouvinte de Cenários roda depois da transação, em outra thread, e a suspensão ou a
 * reativação vai para o log de auditoria (RN-35) pelo ouvinte da Auditoria.
 *
 * <p>Sem {@code @Transactional}, porque os ouvintes só recebem o evento depois da confirmação; por isso cada
 * teste cria os usuários e o cenário, num contexto só dos testes da RF-25, porque grava versões aprovadas
 * (RN-08). O evento publicado é conferido no registro de publicação do Spring
 * Modulith, que guarda cada evento com o ouvinte que o recebe.
 */
@SpringBootTest(properties = "lab.teste.contexto=suspensao")
@Import(InfraestruturaDeTeste.class)
class SuspensaoAutomaticaTest {

    /** Os ids das sinalizações não precisam existir na Auditoria: Cenários guarda só o id. */
    private static final AtomicLong SINALIZACAO = new AtomicLong(System.nanoTime());

    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Autowired
    private ApplicationEventPublisher eventos;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private Cenarios cenarios;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private AprovacaoDeCenario aprovacao;

    @Autowired
    private MeterRegistry metricas;

    @Autowired
    private JdbcTemplate jdbc;

    private long autora;
    private long professora;
    private long curadora;
    private String emailDaCuradora;
    private long outraCuradora;
    private long cenario;
    private long versao;
    private Instant momento;

    @BeforeEach
    void prepararCenarioAprovado() {
        autora = inserirUsuario("autora");
        professora = inserirUsuario("professora");
        emailDaCuradora = "curadora-" + UUID.randomUUID() + "@escola.test";
        curadora = inserirUsuarioComEmail(emailDaCuradora);
        outraCuradora = inserirUsuario("outra-curadora");
        momento = Instant.now().truncatedTo(ChronoUnit.MICROS);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
        Cenario aprovado = aprovar(cenarios.salvar(Cenario.novo(autora, ConteudosDeTeste.completo())));
        cenario = aprovado.id();
        versao = aprovado.versao().id();
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    // RN-23a: a sinalização grave do professor suspende o cenário na hora, por precaução.

    @Test
    void graveDoProfessorSuspendeNaHora() {
        double antes = contador(ContagemDaSuspensao.METRICA_DA_SUSPENSAO, "sinalizacao_grave");
        long sinalizacao = registrar("PROFESSOR", "GRAVE", professora);

        aguardarStatus("SUSPENSO");
        assertThat(linhaDoLog("SUSPENDER_CENARIO", professora)).contains("\"motivo\": \"SINALIZACAO_GRAVE\"")
                .contains("\"versaoId\": " + versao);
        assertThat(publicados("CenarioSuspenso")).isEqualTo(1);
        aguardarContador(ContagemDaSuspensao.METRICA_DA_SUSPENSAO, "sinalizacao_grave", antes + 1);
    }

    /** Quem tem os dois perfis e sinaliza como curador espera a triagem: decide o perfil gravado. */
    @Test
    void graveFeitaComoCuradorNaoSuspendeNaHora() {
        long sinalizacao = registrar("CURADOR", "GRAVE", curadora);

        aguardarOuvinte(sinalizacao, 1);
        assertThat(status()).isEqualTo("APROVADO");
        assertThat(publicados("CenarioSuspenso")).isZero();
    }

    // RN-23b: o erro grave confirmado mantém o cenário suspenso até uma versão nova ser aprovada.

    @Test
    void erroGraveConfirmadoSuspendeAteAVersaoNovaSerAprovada() {
        long grave = registrar("CURADOR", "MODERADA", curadora);
        aguardarOuvinte(grave, 1);
        assertThat(status()).isEqualTo("APROVADO");

        publicar(confirmada(grave, "CURADOR", "MODERADA", "GRAVE", curadora));
        aguardarStatus("SUSPENSO");
        assertThat(linhaDoLog("SUSPENDER_CENARIO", curadora)).contains("\"motivo\": \"ERRO_GRAVE_CONFIRMADO\"");

        // Descartar outra sinalização da versão não tira o erro grave confirmado.
        long outra = registrar("PROFESSOR", "LEVE", professora);
        publicar(descartada(outra, "PROFESSOR", "LEVE", curadora));
        aguardarOuvinte(outra, 2);
        assertThat(status()).isEqualTo("SUSPENSO");

        aprovarVersaoNovaPelaCuradoria();
        assertThat(status()).isEqualTo("APROVADO");
        assertThat(publicados("CenarioReativado")).isEqualTo(1);
        assertThat(linhaDoLog("REATIVAR_CENARIO", curadora)).contains("\"motivo\": \"NOVA_VERSAO_APROVADA\"");
    }

    // RN-23b: a aprovação da versão nova pela curadoria tira o cenário da suspensão.

    /** Duas ações, dois eventos e uma linha de cada no log (RN-35): a aprovação e a reativação. */
    @Test
    void aprovacaoDaVersaoNovaReativaOCenarioSuspenso() {
        double antes = contador(ContagemDaSuspensao.METRICA_DA_REATIVACAO, "nova_versao_aprovada");
        registrar("PROFESSOR", "GRAVE", professora);
        aguardarStatus("SUSPENSO");

        long nova = aprovarVersaoNovaPelaCuradoria();

        assertThat(status()).isEqualTo("APROVADO");
        assertThat(publicados("CenarioReativado")).isEqualTo(1);
        assertThat(publicados("CenarioAprovado")).isEqualTo(1);
        assertThat(linhaDoLog("REATIVAR_CENARIO", curadora)).contains("\"motivo\": \"NOVA_VERSAO_APROVADA\"")
                .contains("\"versaoId\": " + nova);
        assertThat(linhaDoLog("APROVAR_CENARIO", curadora)).contains("\"versaoId\": " + nova);
        aguardarContador(ContagemDaSuspensao.METRICA_DA_REATIVACAO, "nova_versao_aprovada", antes + 1);
    }

    @Test
    void aprovacaoDeCenarioQueNaoEstavaSuspensoNaoReativa() {
        double antes = contador(ContagemDaSuspensao.METRICA_DA_REATIVACAO, "nova_versao_aprovada");

        aprovarVersaoNovaPelaCuradoria();

        assertThat(status()).isEqualTo("APROVADO");
        linhaDoLog("APROVAR_CENARIO", curadora);
        assertThat(publicados("CenarioReativado")).isZero();
        assertThat(contador(ContagemDaSuspensao.METRICA_DA_REATIVACAO, "nova_versao_aprovada")).isEqualTo(antes);
    }

    /** RN-06: sem o teste do personagem a aprovação é recusada, e o cenário continua suspenso. */
    @Test
    void aprovacaoRecusadaNaoReativa() {
        registrar("PROFESSOR", "GRAVE", professora);
        aguardarStatus("SUSPENSO");
        enviarVersaoNova();
        logarCuradora();

        assertThatThrownBy(() -> aprovacao.aprovar(cenario, TesteDoPersonagemFeito.CHECKLIST))
                .isInstanceOf(AprovacaoIncompleta.class);

        assertThat(status()).isEqualTo("SUSPENSO");
        assertThat(publicados("CenarioAprovado")).isZero();
        assertThat(publicados("CenarioReativado")).isZero();
    }

    /**
     * A reativação sai na mesma transação da aprovação: desfeita a transação depois da aprovação, nada fica,
     * nem o status, nem o evento, nem a métrica.
     */
    @Test
    void reativacaoDesfeitaComATransacaoDaAprovacao() {
        double antes = contador(ContagemDaSuspensao.METRICA_DA_REATIVACAO, "nova_versao_aprovada");
        registrar("PROFESSOR", "GRAVE", professora);
        aguardarStatus("SUSPENSO");
        enviarVersaoNova();
        TesteDoPersonagemFeito.pelo(jdbc, emailDaCuradora, cenario);
        logarCuradora();

        transacao.executeWithoutResult(status -> {
            assertThat(aprovacao.aprovar(cenario, TesteDoPersonagemFeito.CHECKLIST).status()).hasToString("APROVADO");
            status.setRollbackOnly();
        });

        assertThat(status()).isEqualTo("SUSPENSO");
        assertThat(publicados("CenarioAprovado")).isZero();
        assertThat(publicados("CenarioReativado")).isZero();
        assertThat(contador(ContagemDaSuspensao.METRICA_DA_REATIVACAO, "nova_versao_aprovada")).isEqualTo(antes);
    }

    /** Com a versão nova em sala, a triagem tardia de um erro grave da versão antiga não suspende de novo. */
    @Test
    void triagemTardiaDaVersaoAntigaNaoSuspendeDeNovo() {
        long antiga = versao;
        long sinalizacao = registrar("PROFESSOR", "GRAVE", professora);
        aguardarStatus("SUSPENSO");
        aprovarVersaoNovaPelaCuradoria();

        publicar(new SinalizacaoTriada(
                sinalizacao, antiga, "CONFIRMADA", "GRAVE", "GRAVE", "PROFESSOR", curadora, momento));

        aguardarOuvinte(sinalizacao, 2);
        assertThat(status()).isEqualTo("APROVADO");
        assertThat(publicados("CenarioSuspenso")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT gravidade_final FROM sinalizacao_do_cenario WHERE sinalizacao_id = ?", String.class,
                sinalizacao))
                .isEqualTo("GRAVE");
    }

    // RN-23c: três erros moderados confirmados na mesma versão também suspendem.

    @Test
    void tresModeradosConfirmadosSuspendem() {
        long primeira = registrar("CURADOR", "MODERADA", curadora);
        long segunda = registrar("PROFESSOR", "LEVE", professora);
        long terceira = registrar("PROFESSOR", "MODERADA", professora);

        publicar(confirmada(primeira, "CURADOR", "MODERADA", "MODERADA", curadora));
        publicar(confirmada(segunda, "PROFESSOR", "LEVE", "MODERADA", curadora));
        aguardarOuvinte(primeira, 2);
        aguardarOuvinte(segunda, 2);
        assertThat(status()).isEqualTo("APROVADO");

        publicar(confirmada(terceira, "PROFESSOR", "MODERADA", "MODERADA", outraCuradora));
        aguardarStatus("SUSPENSO");
        assertThat(linhaDoLog("SUSPENDER_CENARIO", outraCuradora)).contains("\"motivo\": \"TRES_ERROS_MODERADOS\"");
    }

    // RN-23d: se o curador descartar a sinalização que causou a suspensão, o cenário volta a Aprovado.

    @Test
    void descarteDaSinalizacaoQueSuspendeuReativa() {
        double antes = contador(ContagemDaSuspensao.METRICA_DA_REATIVACAO, "sinalizacao_descartada");
        long sinalizacao = registrar("PROFESSOR", "GRAVE", professora);
        aguardarStatus("SUSPENSO");

        publicar(descartada(sinalizacao, "PROFESSOR", "GRAVE", curadora));

        aguardarStatus("APROVADO");
        assertThat(linhaDoLog("REATIVAR_CENARIO", curadora)).contains("\"motivo\": \"SINALIZACAO_DESCARTADA\"")
                .contains("\"versaoId\": " + versao);
        assertThat(publicados("CenarioReativado")).isEqualTo(1);
        aguardarContador(ContagemDaSuspensao.METRICA_DA_REATIVACAO, "sinalizacao_descartada", antes + 1);
    }

    /** Confirmada com gravidade menor, a suspensão de precaução cai. */
    @Test
    void rebaixamentoDaGraveDoProfessorReativa() {
        long sinalizacao = registrar("PROFESSOR", "GRAVE", professora);
        aguardarStatus("SUSPENSO");

        publicar(confirmada(sinalizacao, "PROFESSOR", "GRAVE", "MODERADA", curadora));

        aguardarStatus("APROVADO");
        assertThat(linhaDoLog("REATIVAR_CENARIO", curadora)).contains("\"motivo\": \"GRAVIDADE_REBAIXADA\"");
    }

    @Test
    void descarteDeSinalizacaoQueNaoSuspendeuNaoReativa() {
        registrar("PROFESSOR", "GRAVE", professora);
        aguardarStatus("SUSPENSO");
        long outra = registrar("CURADOR", "MODERADA", curadora);

        publicar(descartada(outra, "CURADOR", "MODERADA", curadora));

        aguardarOuvinte(outra, 2);
        assertThat(status()).isEqualTo("SUSPENSO");
        assertThat(publicados("CenarioReativado")).isZero();
    }

    // Reentrega e ordem dos eventos.

    /**
     * O Spring Modulith pode entregar o mesmo evento de novo, depois da primeira entrega, quando reprocessa os
     * pendentes: a sinalização conta uma vez só. As duas entregas saem uma depois da outra, como na reentrega;
     * duas publicações idênticas ao mesmo tempo confundem a marcação de concluída do registro de publicação, que
     * procura pelo evento serializado.
     */
    @Test
    void reentregaDoMesmoEventoGeraUmEventoEUmaLinhaNoLog() {
        var evento = registrada(SINALIZACAO.incrementAndGet(), "PROFESSOR", "GRAVE", professora);

        publicar(evento);
        aguardarOuvinte(evento.sinalizacaoId(), 1);
        publicar(evento);

        aguardarOuvinte(evento.sinalizacaoId(), 2);
        assertThat(status()).isEqualTo("SUSPENSO");
        assertThat(publicados("CenarioSuspenso")).isEqualTo(1);
        linhaDoLog("SUSPENDER_CENARIO", professora);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sinalizacao_do_cenario WHERE sinalizacao_id = ?", Integer.class,
                evento.sinalizacaoId()))
                .isEqualTo(1);
    }

    /** Na reentrega dos pendentes, a triagem pode chegar antes: a grave já rebaixada não suspende depois. */
    @Test
    void triagemQueChegaAntesDoRegistroNaoSuspendePorPrecaucao() {
        long sinalizacao = SINALIZACAO.incrementAndGet();

        publicar(confirmada(sinalizacao, "PROFESSOR", "GRAVE", "LEVE", curadora));
        aguardarOuvinte(sinalizacao, 1);
        publicar(registrada(sinalizacao, "PROFESSOR", "GRAVE", professora));
        aguardarOuvinte(sinalizacao, 2);

        assertThat(status()).isEqualTo("APROVADO");
        assertThat(publicados("CenarioSuspenso")).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT decisao FROM sinalizacao_do_cenario WHERE sinalizacao_id = ?", String.class, sinalizacao))
                .isEqualTo("CONFIRMADA");
    }

    // Casos ignorados: a sinalização é guardada, mas o cenário não muda.

    @Test
    void sinalizacaoDeVersaoQueNaoEMaisAVigenteEIgnorada() {
        long antiga = versao;
        aprovarVersaoNova();

        long sinalizacao = publicarRegistrada(antiga, "PROFESSOR", "GRAVE", professora);

        aguardarOuvinte(sinalizacao, 1);
        assertThat(status()).isEqualTo("APROVADO");
        assertThat(publicados("CenarioSuspenso")).isZero();
        assertThat(sinalizacaoGuardada(sinalizacao)).isTrue();
    }

    @Test
    void cenarioArquivadoEIgnorado() {
        cenarios.salvar(cenarios.buscar(cenario).orElseThrow().arquivar(new Revisor(curadora, true)));

        long sinalizacao = registrar("PROFESSOR", "GRAVE", professora);

        aguardarOuvinte(sinalizacao, 1);
        assertThat(status()).isEqualTo("ARQUIVADO");
        assertThat(publicados("CenarioSuspenso")).isZero();
        assertThat(sinalizacaoGuardada(sinalizacao)).isTrue();
    }

    /** Nunca aprovado, o cenário não tem versão vigente nem foi para a sala. */
    @Test
    void cenarioEmCuradoriaEIgnorado() {
        Cenario emCuradoria = cenarios.salvar(
                cenarios.salvar(Cenario.novo(autora, ConteudosDeTeste.completo())).enviarACuradoria(autora));
        cenario = emCuradoria.id();

        long sinalizacao = publicarRegistrada(emCuradoria.versao().id(), "PROFESSOR", "GRAVE", professora);

        aguardarOuvinte(sinalizacao, 1);
        assertThat(status()).isEqualTo("EM_CURADORIA");
        assertThat(publicados("CenarioSuspenso")).isZero();
        assertThat(sinalizacaoGuardada(sinalizacao)).isTrue();
    }

    /** Sem a versão não há onde guardar; o evento termina, em vez de ficar pendente para sempre. */
    @Test
    void versaoDesconhecidaEIgnorada() {
        long sinalizacao = publicarRegistrada(-1, "PROFESSOR", "GRAVE", professora);

        aguardarOuvinte(sinalizacao, 1);
        assertThat(sinalizacaoGuardada(sinalizacao)).isFalse();
    }

    private long registrar(String perfil, String gravidade, long por) {
        return publicarRegistrada(versao, perfil, gravidade, por);
    }

    private long publicarRegistrada(long naVersao, String perfil, String gravidade, long por) {
        long id = SINALIZACAO.incrementAndGet();
        publicar(new SinalizacaoRegistrada(id, 1, naVersao, "ERRO_HISTORICO", gravidade, perfil, por, momento));
        return id;
    }

    private SinalizacaoRegistrada registrada(long id, String perfil, String gravidade, long por) {
        return new SinalizacaoRegistrada(id, 1, versao, "ERRO_HISTORICO", gravidade, perfil, por, momento);
    }

    private SinalizacaoTriada confirmada(long id, String perfil, String percebida, String gravidadeFinal, long por) {
        return new SinalizacaoTriada(id, versao, "CONFIRMADA", gravidadeFinal, percebida, perfil, por, momento);
    }

    private SinalizacaoTriada descartada(long id, String perfil, String percebida, long por) {
        return new SinalizacaoTriada(id, versao, "DESCARTADA", null, percebida, perfil, por, momento);
    }

    private void publicar(Object evento) {
        transacao.executeWithoutResult(status -> eventos.publishEvent(evento));
    }

    private Cenario aprovar(Cenario rascunho) {
        Cenario enviado = cenarios.salvar(rascunho.enviarACuradoria(autora));
        return cenarios.salvar(enviado.aprovar(new Revisor(curadora, true), ConteudosDeTeste.CONFERIDA, momento));
    }

    /** Como o domínio aprova; o evento da reativação na aprovação é da AprovacaoDeCenario. */
    private void aprovarVersaoNova() {
        Cenario comNova = cenarios.salvar(cenarios.buscar(cenario).orElseThrow().novaVersao(autora));
        versao = aprovar(comNova).versao().id();
    }

    /** A autora cria a versão nova e a envia à curadoria, como o domínio faz. */
    private void enviarVersaoNova() {
        Cenario comNova = cenarios.salvar(cenarios.buscar(cenario).orElseThrow().novaVersao(autora));
        cenarios.salvar(comNova.enviarACuradoria(autora));
    }

    /** A curadora testa o personagem e aprova pela AprovacaoDeCenario, como na tela. */
    private long aprovarVersaoNovaPelaCuradoria() {
        enviarVersaoNova();
        TesteDoPersonagemFeito.pelo(jdbc, emailDaCuradora, cenario);
        logarCuradora();
        versao = aprovacao.aprovar(cenario, TesteDoPersonagemFeito.CHECKLIST).versao().id();
        SecurityContextHolder.clearContext();
        return versao;
    }

    private void logarCuradora() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(emailDaCuradora, null, "ROLE_CURADOR"));
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM cenario WHERE id = ?", String.class, cenario);
    }

    private void aguardarStatus(String esperado) {
        await().atMost(ESPERA).until(() -> esperado.equals(status()));
    }

    /** O ouvinte de Cenários concluiu tantas entregas da sinalização, contando registro e triagem. */
    private void aguardarOuvinte(long sinalizacao, int entregas) {
        await().atMost(ESPERA).until(() -> jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE listener_id LIKE '%OuvinteDeSinalizacoes%' AND completion_date IS NOT NULL
                  AND serialized_event LIKE ?""", Integer.class, "%\"sinalizacaoId\":" + sinalizacao + ",%")
                >= entregas);
    }

    /** Os eventos que Cenários publicou sobre o cenário, como o log de auditoria os recebe. */
    private int publicados(String evento) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE event_type LIKE ? AND listener_id LIKE '%OuvinteDeAcoesCriticas%' AND serialized_event LIKE ?""",
                Integer.class, "%." + evento, "%\"cenarioId\":" + cenario + ",%");
    }

    /** Espera a única linha da ação no log e confere o responsável; devolve os detalhes. */
    private String linhaDoLog(String acao, long responsavel) {
        String consulta = """
                SELECT usuario_id, detalhes::text AS detalhes FROM log_auditoria
                WHERE acao = ? AND entidade_id = ?""";
        await().atMost(ESPERA).until(() -> !jdbc.queryForList(consulta, acao, String.valueOf(cenario)).isEmpty());
        List<Map<String, Object>> linhas = jdbc.queryForList(consulta, acao, String.valueOf(cenario));
        assertThat(linhas).as("linhas %s do log", acao).hasSize(1);
        assertThat(linhas.getFirst().get("usuario_id")).isEqualTo(responsavel);
        return linhas.getFirst().get("detalhes").toString();
    }

    private boolean sinalizacaoGuardada(long sinalizacao) {
        return jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM sinalizacao_do_cenario WHERE sinalizacao_id = ?)", Boolean.class,
                sinalizacao);
    }

    /** A métrica é registrada depois da confirmação, na thread do ouvinte. */
    private void aguardarContador(String metrica, String motivo, double esperado) {
        await().atMost(ESPERA).until(() -> contador(metrica, motivo) == esperado);
    }

    private double contador(String metrica, String motivo) {
        Counter contador = metricas.find(metrica).tag("motivo", motivo).counter();
        return contador == null ? 0 : contador.count();
    }

    private long inserirUsuario(String prefixo) {
        return inserirUsuarioComEmail(prefixo + "-" + UUID.randomUUID() + "@escola.test");
    }

    private long inserirUsuarioComEmail(String email) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class,
                email);
    }
}
