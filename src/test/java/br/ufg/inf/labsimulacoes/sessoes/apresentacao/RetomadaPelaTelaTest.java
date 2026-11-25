package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Critérios de aceite da issue 36 pela tela, com requisições HTTP reais: recarregar a página mantém a
 * conversa e o estado, o Redis sem a chave não perde a sessão, e a falha do provedor permite tentar de
 * novo sem recarregar (RF-20, RNF-04). Mesmo contexto do {@link ProjecaoPelaTelaTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lab.teste.contexto=conversa-pela-tela")
@Import(InfraestruturaDeTeste.class)
class RetomadaPelaTelaTest {

    private static final String SENHA = "uma senha de teste";
    private static final Pattern SESSAO = Pattern.compile("/sessoes/(\\d+)$");

    @LocalServerPort
    private int porta;

    @MockitoBean
    private AdaptadorDeIa adaptador;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    private Navegador professora;
    private long sessao;

    @BeforeEach
    void iniciarSessao() throws Exception {
        var dados = new DadosDeSessao(jdbc);
        long turma = dados.turma();
        long cenario = dados.cenarioAprovado(dados.idDe(dados.usuario("PROFESSOR")), dados.idDe(dados.usuario("CURADOR")))
                .cenarioId();
        String email = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Pessoa de Teste", email, SENHA, Set.of(Perfil.PROFESSOR));
        professora = new Navegador(porta);
        professora.entrar(email, SENHA);
        when(adaptador.moderar(anyString())).thenReturn(ResultadoDaModeracao.aprovado());
        var inicio = professora.postHtmx("/sessoes", "turma=" + turma + "&cenario=" + cenario);
        Matcher id = SESSAO.matcher(inicio.headers().firstValue("Location").orElse(""));
        assertThat(id.find()).isTrue();
        sessao = Long.parseLong(id.group(1));
    }

    /** Critério de aceite: com o Redis sem a chave, recarregar a página mantém a conversa e o estado. */
    @Test
    void recarregarSemAChaveDoRedisMantemAConversaEOEstado() throws Exception {
        respondeCom("Foi em 1906.");
        professora.perguntar(sessao, "Quando foi o voo?");
        professora.resposta(sessao);
        professora.postHtmx("/sessoes/" + sessao + "/pausar", "");
        redis.delete("sessao:" + sessao + ":estado");

        String corpo = professora.get("/sessoes/" + sessao).body();

        assertThat(corpo).contains("Quando foi o voo?").contains("Foi em 1906.").contains("1/40 perguntas")
                .contains("Pausada").contains("data-pode-perguntar=\"false\"");
    }

    /** O canal reaberto depois da resposta, como numa reconexão, entrega a resposta gravada. */
    @Test
    void canalReabertoDepoisDaRespostaEntregaARespostaGravada() throws Exception {
        respondeCom("Foi em 1906.");
        professora.perguntar(sessao, "Quando foi o voo?");
        professora.resposta(sessao);

        HttpResponse<String> reaberto = professora.resposta(sessao);

        assertThat(reaberto.body()).containsSubsequence("event:trecho\ndata:Foi em 1906.\n", "event:fim\n");
        verify(adaptador, times(1)).responder(any(), any());
    }

    /** Critério de aceite: a falha do provedor gera o aviso, e tentar de novo reabre só a resposta. */
    @Test
    void falhaDoProvedorAvisaETentarDeNovoResponde() throws Exception {
        when(adaptador.responder(any(), any())).thenThrow(new FalhaNoProvedorDeIa("fora do ar", null));
        professora.perguntar(sessao, "Quando foi o voo?");
        assertThat(professora.resposta(sessao).body()).contains("event:falha").contains("Tente de novo");

        String novaResposta = professora.get("/sessoes/" + sessao + "/resposta-pendente").body();
        respondeCom("Foi em 1906.");

        assertThat(novaResposta).contains("sse-connect=\"/sessoes/" + sessao + "/resposta\"")
                .contains("data-reabrir=\"/sessoes/" + sessao + "/resposta-pendente\"")
                .doesNotContain("<html");
        assertThat(professora.resposta(sessao).body()).contains("event:trecho\ndata:Foi em 1906.\n");
    }

    @Test
    void outraProfessoraNaoReabreARespostaDaSessao() throws Exception {
        String email = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Outra Pessoa", email, SENHA, Set.of(Perfil.PROFESSOR));
        var outra = new Navegador(porta);
        outra.entrar(email, SENHA);

        assertThat(outra.get("/sessoes/" + sessao + "/resposta-pendente").statusCode()).isEqualTo(403);
    }

    private void respondeCom(String texto) {
        doAnswer(chamada -> {
            Consumer<String> aoReceber = chamada.getArgument(1);
            aoReceber.accept(texto);
            long metadados = jdbc.queryForObject(
                    "INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt) VALUES ('openai', 'm', 'v1') RETURNING id",
                    Long.class);
            return new RespostaDoPersonagem(texto, metadados);
        }).when(adaptador).responder(any(), any());
    }
}
