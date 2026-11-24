package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.ia.aplicacao.BloqueioPorPadroes;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.InicioDeSessao;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * RF-21 e RN-13 na sala, pela tela: a pergunta com dado pessoal não chega ao provedor, nem pela
 * moderação. O adaptador de IA é o real e só o provedor é simulado; os dados são inventados. A
 * configuração é a mesma dos outros testes de dados pessoais, para o contexto ser um só.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lab.teste.contexto=dados-pessoais")
@Import(InfraestruturaDeTeste.class)
class DadosPessoaisNaSalaTest {

    private static final String SENHA = "uma senha de teste";

    @LocalServerPort
    private int porta;

    @MockitoBean
    private ProvedorDeIa provedor;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private InicioDeSessao inicio;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry metricas;

    private long sessao;
    private Navegador navegador;

    @BeforeEach
    void entrarEIniciarSessao() throws Exception {
        var dados = new DadosDeSessao(jdbc);
        String professora = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Profissional", professora, SENHA, Set.of(Perfil.PROFESSOR));
        long cenario = dados.cenarioAprovado(dados.idDe(dados.usuario("PROFESSOR")), dados.idDe(dados.usuario("CURADOR")))
                .cenarioId();
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(professora, null, "ROLE_PROFESSOR"));
        try {
            sessao = inicio.iniciar(dados.turma(), cenario).id();
        } finally {
            SecurityContextHolder.clearContext();
        }
        navegador = new Navegador(porta);
        navegador.entrar(professora, SENHA);
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Nada sai para o provedor e nada é gravado: nem mensagem, nem caso na auditoria. A sessão segue em
     * andamento, a pergunta não conta no limite (RN-18) e o aviso nomeia o tipo sem repetir o dado.
     */
    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(delimiter = '|', textBlock = """
            Mandem para fulano@exemplo.com                | e-mail   | fulano@exemplo.com
            O número dele é (62) 98765-4321               | telefone | 98765-4321
            O CPF é 123.456.789-00                        | CPF      | 123.456.789-00
            Ele mora na Rua das Flores, 123               | endereço | Rua das Flores
            """)
    void perguntaComDadoPessoalNaoChegaAoProvedorNemEGravada(String pergunta, String tipo, String dado)
            throws Exception {
        var resposta = navegador.perguntar(sessao, pergunta);

        assertThat(resposta.statusCode()).isEqualTo(422);
        assertThat(resposta.headers().firstValue("HX-Retarget")).contains("#aviso-da-sessao");
        assertThat(resposta.body())
                .contains("A pergunta parece ter um dado pessoal (" + tipo + ").")
                .contains("Reescreva sem e-mail, telefone, CPF ou endereço, e não digite nomes de estudantes.")
                .doesNotContain(dado);
        verifyNoInteractions(provedor);
        assertThat(contar("SELECT count(*) FROM mensagem WHERE sessao_id = ?")).isZero();
        assertThat(contar("SELECT count(*) FROM caso_bloqueado WHERE sessao_id = ?")).isZero();
        assertThat(contar("SELECT count(*) FROM sessao WHERE id = ? AND status = 'EM_ANDAMENTO'")).isEqualTo(1);
        assertThat(navegador.get("/sessoes/" + sessao + "/painel").body()).contains("0/40 perguntas");
    }

    /** Depois de reescrever, a pergunta vai normalmente e é a primeira a contar no limite. */
    @Test
    void perguntaReescritaSegueNormalmente() throws Exception {
        navegador.perguntar(sessao, "Liguem para 98765-4321");
        when(provedor.moderar(anyString())).thenReturn(ResultadoDaModeracao.aprovado());

        var resposta = navegador.perguntar(sessao, "Como foi o primeiro voo?");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(contar("SELECT count(*) FROM mensagem WHERE sessao_id = ?")).isEqualTo(1);
        assertThat(navegador.get("/sessoes/" + sessao + "/painel").body()).contains("1/40 perguntas");
    }

    /**
     * O bloqueio vem antes da moderação: um texto impróprio com telefone não vai ao provedor nem abre caso
     * na fila do curador, que copiaria o texto (RN-20).
     */
    @Test
    void textoImproprioComDadoPessoalNaoAbreCaso() throws Exception {
        var resposta = navegador.perguntar(sessao, "Vou te bater, me liga no 98765-4321");

        assertThat(resposta.statusCode()).isEqualTo(422);
        verifyNoInteractions(provedor);
        assertThat(contar("SELECT count(*) FROM caso_bloqueado WHERE sessao_id = ?")).isZero();
        assertThat(contar("SELECT count(*) FROM sessao WHERE id = ? AND status = 'EM_ANDAMENTO'")).isEqualTo(1);
    }

    @Test
    void bloqueioContaNaMetricaSoPeloTipo() throws Exception {
        double antes = bloqueios("cpf");

        navegador.perguntar(sessao, "CPF 12345678900");

        assertThat(bloqueios("cpf")).isEqualTo(antes + 1);
        assertThat(metricas.find(BloqueioPorPadroes.METRICA).counters())
                .allSatisfy(contador -> assertThat(contador.getId().getTags())
                        .extracting(tag -> tag.getKey())
                        .containsExactly(BloqueioPorPadroes.TIPO));
    }

    /** D7: o limite do campo vale também no servidor, antes de conferir ou enviar qualquer coisa. */
    @Test
    void perguntaLongaDemaisNaoChegaAoProvedor() throws Exception {
        var resposta = navegador.perguntar(sessao, "a".repeat(1001));

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.body()).contains("A pergunta tem no máximo 1000 caracteres.");
        verifyNoInteractions(provedor);
        assertThat(contar("SELECT count(*) FROM mensagem WHERE sessao_id = ?")).isZero();
    }

    private long contar(String sql) {
        return jdbc.queryForObject(sql, Long.class, sessao);
    }

    private double bloqueios(String tipo) {
        var contador = metricas.find(BloqueioPorPadroes.METRICA).tag(BloqueioPorPadroes.TIPO, tipo).counter();
        return contador == null ? 0 : contador.count();
    }
}
