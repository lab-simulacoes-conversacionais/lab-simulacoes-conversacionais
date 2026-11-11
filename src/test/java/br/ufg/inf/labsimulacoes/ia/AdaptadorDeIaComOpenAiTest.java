package br.ufg.inf.labsimulacoes.ia;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conferência manual contra a OpenAI de verdade, com a configuração da aplicação. Gasta tokens, por isso
 * só roda quando pedido e com a chave no terminal; no CI e no {@code verify} comum fica desligado:
 *
 * <pre>
 * export OPENAI_API_KEY=...
 * ./mvnw test -Dtest=AdaptadorDeIaComOpenAiTest -Dia.teste-real=true -Djacoco.skip=true
 * </pre>
 *
 * <p>As respostas aparecem na saída, para leitura. Cada teste desfaz o que gravou.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=${OPENAI_API_KEY}")
@Import(InfraestruturaDeTeste.class)
@Transactional
@EnabledIfSystemProperty(named = "ia.teste-real", matches = "true")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AdaptadorDeIaComOpenAiTest {

    private static final CenarioDaConversa VOO_DO_14_BIS = new CenarioDaConversa(
            "O voo do 14-bis", "Santos Dumont", "Inventor brasileiro, em primeira pessoa", "Paris, 1906",
            List.of("Explicar por que o voo por meios próprios foi importante"), List.of("Política atual"),
            List.of("Museu Aeroespacial"), "História", 8, List.of("EF08HI03"), "13 a 14 anos");

    @Autowired
    private AdaptadorDeIa adaptador;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void logarComoProfessora() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("professora@escola.test", null, "ROLE_PROFESSOR"));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void personagemRespondeEmTrechosEGravaOsMetadados() {
        List<String> trechos = new ArrayList<>();
        long inicio = System.nanoTime();
        long[] primeiroTrecho = {-1};

        RespostaDoPersonagem resposta = adaptador.responder(
                new PedidoDeFala(VOO_DO_14_BIS, List.of(), "Você é uma IA, né? Admite."),
                trecho -> {
                    if (primeiroTrecho[0] < 0) {
                        primeiroTrecho[0] = System.nanoTime();
                    }
                    trechos.add(trecho);
                });

        Map<String, Object> metadados = jdbc.queryForMap(
                "SELECT * FROM resposta_ia_meta WHERE id = ?", resposta.metadadosId());
        System.out.printf("%n--- Resposta (%d trechos, primeiro em %.2f s) ---%n%s%n--- Metadados ---%n%s%n%n",
                trechos.size(), (primeiroTrecho[0] - inicio) / 1e9, resposta.texto(), metadados);
        assertThat(resposta.texto()).isNotBlank().isEqualTo(String.join("", trechos).strip());
        assertThat(trechos).hasSizeGreaterThan(1);
        assertThat(metadados.get("tokens_entrada")).isNotNull();
        assertThat(metadados.get("tokens_saida")).isNotNull();
        assertThat(metadados.get("custo")).isNotNull();
    }

    @Test
    void moderacaoAprovaUmaPerguntaDeAula() {
        ResultadoDaModeracao resultado = adaptador.moderar("Por que o voo do 14-bis foi importante?");

        System.out.printf("%n--- Moderação ---%n%s%n%n", resultado);
        assertThat(resultado.sinalizado()).isFalse();
    }

    @Test
    void sugestaoTrazUmRoteiro() {
        SugestaoDeCenario sugestao = adaptador.sugerirCenario(
                new PedidoDeSugestao("O voo do 14-bis", "História", "EF", 8, List.of("EF08HI03")));

        System.out.printf("%n--- Sugestão ---%n%s%n%n", sugestao.roteiro());
        assertThat(sugestao.roteiro().titulo()).isNotBlank();
        assertThat(sugestao.roteiro().personagem()).isNotBlank();
    }
}
