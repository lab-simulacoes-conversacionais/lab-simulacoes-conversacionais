package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.LimiteDoTesteAtingido;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * RNF-13: o teto de perguntas de teste vale também com pedidos ao mesmo tempo. Sem {@code
 * @Transactional}, para cada pedido confirmar as próprias transações como em produção; por isso cada
 * teste cria usuários e cenário novos. O {@code @MockitoBean} é o mesmo dos outros testes do caso de uso,
 * e o contexto é reaproveitado.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
class TesteDoCenarioConcorrenciaTest {

    private static final int PEDIDOS = 10;
    private static final int TETO = 40;

    @MockitoBean
    private AdaptadorDeIa adaptador;

    @Autowired
    private TesteDoCenario teste;

    @Autowired
    private AutoriaDeCenarios autoria;

    @Autowired
    private EnvioACuradoria envio;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    private String curadora;
    private long curadoraId;
    private long cenario;
    private long versao;

    @BeforeEach
    void prepararCenarioEmCuradoria() {
        String autora = inserirUsuario();
        curadora = inserirUsuario();
        curadoraId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, curadora);
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
        logar(autora, "ROLE_PROFESSOR");
        cenario = autoria.criar(ConteudosDeTeste.completo()).id();
        versao = envio.enviar(cenario).versao().id();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    /** Com 39 das 40 vagas usadas, 10 pedidos ao mesmo tempo: só um passa, e a rodada fica em 40. */
    @Test
    void dezPedidosAoMesmoTempoNaoPassamDoTeto() throws Exception {
        for (int i = 0; i < TETO - 1; i++) {
            inserirPerguntaConcluida("Pergunta " + i);
        }

        int passaram = pedirAoMesmoTempo(PEDIDOS);

        assertThat(passaram).isEqualTo(1);
        assertThat(linhasDaRodada()).isEqualTo(TETO);
    }

    /** Com a rodada vazia, 50 pedidos ao mesmo tempo: passam exatamente 40, nem um a mais. */
    @Test
    void pedidosAoMesmoTempoParamExatamenteNoTeto() throws Exception {
        int passaram = pedirAoMesmoTempo(TETO + PEDIDOS);

        assertThat(passaram).isEqualTo(TETO);
        assertThat(linhasDaRodada()).isEqualTo(TETO);
    }

    /**
     * Os pedidos saem juntos, e o filtro simulado segura cada um até todos chegarem, ou até 3 s, para os
     * pedidos se sobreporem como na corrida real.
     *
     * @return quantos pedidos passaram; os outros foram recusados pelo teto
     */
    private int pedirAoMesmoTempo(int quantos) throws Exception {
        CountDownLatch todosChegaram = new CountDownLatch(quantos);
        when(adaptador.moderar(anyString())).thenAnswer(chamada -> {
            todosChegaram.countDown();
            todosChegaram.await(3, TimeUnit.SECONDS);
            return ResultadoDaModeracao.aprovado();
        });
        when(adaptador.responder(any(), any())).thenAnswer(chamada -> new RespostaDoPersonagem("Resposta", novoMetadado()));

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pedidos = Executors.newFixedThreadPool(quantos);
        List<Future<Boolean>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < quantos; i++) {
                String texto = "Pedido simultâneo " + i;
                resultados.add(pedidos.submit(() -> {
                    logar(curadora, "ROLE_CURADOR");
                    try {
                        largada.await();
                        teste.perguntar(cenario, texto);
                        return true;
                    } catch (LimiteDoTesteAtingido e) {
                        return false;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            largada.countDown();
            int passaram = 0;
            for (Future<Boolean> resultado : resultados) {
                passaram += resultado.get(60, TimeUnit.SECONDS) ? 1 : 0;
            }
            return passaram;
        } finally {
            pedidos.shutdownNow();
        }
    }

    private void inserirPerguntaConcluida(String pergunta) {
        jdbc.update("""
                INSERT INTO pergunta_de_teste (cenario_versao_id, curador_id, pergunta, situacao, resposta,
                                               resposta_ia_meta_id)
                VALUES (?, ?, ?, 'CONCLUIDA', 'Resposta', ?)""", versao, curadoraId, pergunta, novoMetadado());
    }

    private long novoMetadado() {
        return jdbc.queryForObject("""
                INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt)
                VALUES ('openai', 'modelo', 'personagem-v1') RETURNING id""", Long.class);
    }

    private int linhasDaRodada() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM pergunta_de_teste WHERE cenario_versao_id = ? AND curador_id = ? AND devolucao_id IS NULL",
                Integer.class, versao, curadoraId);
    }

    private String inserirUsuario() {
        String email = UUID.randomUUID() + "@escola.test";
        jdbc.update("INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash')", email);
        return email;
    }

    private static void logar(String email, String... perfis) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, perfis));
    }
}
