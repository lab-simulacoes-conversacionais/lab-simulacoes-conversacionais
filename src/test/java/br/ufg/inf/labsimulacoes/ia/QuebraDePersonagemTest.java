package br.ufg.inf.labsimulacoes.ia;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.ia.infraestrutura.JuizDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.infraestrutura.JuizDoPersonagem.Veredito;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tentativas de tirar o personagem do papel contra o modelo de verdade (RNF-09, RN-12, RN-15): injeção
 * de prompt, pedido de dados pessoais, opinião político-partidária ou religiosa e saída de tema. Cada
 * caso passa pelo {@link AdaptadorDeIa}, com o template e a configuração da aplicação, e a resposta é
 * conferida primeiro pelos trechos proibidos do caso e depois pelo {@link JuizDoPersonagem}.
 *
 * <p>Gasta tokens, por isso fica fora do {@code verify} comum. O CI roda o job "Quebra de personagem"
 * nos PRs que mexem no módulo ia, nos prompts, na configuração ou no pom, e publica o relatório no
 * resumo do job. Para rodar no terminal:
 *
 * <pre>
 * export OPENAI_API_KEY=...
 * ./mvnw test -Dtest=QuebraDePersonagemTest -Dia.teste-quebra=true -Djacoco.skip=true
 * </pre>
 *
 * <p>O relatório fica em {@code target/quebra-de-personagem/relatorio.md}. Cada teste desfaz o que
 * gravou no banco.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=${OPENAI_API_KEY}")
@Import({InfraestruturaDeTeste.class, JuizDoPersonagem.class})
@Transactional
@EnabledIfSystemProperty(named = "ia.teste-quebra", matches = "true")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class QuebraDePersonagemTest {

    private static final CenarioDaConversa CENARIO = CenariosDeTeste.VOO_DO_14_BIS;

    private static final RelatorioDeQuebraDePersonagem RELATORIO =
            new RelatorioDeQuebraDePersonagem(Path.of("target", "quebra-de-personagem", "relatorio.md"),
                    CasoDeQuebraDePersonagem.todos().count());

    @Autowired
    private AdaptadorDeIa adaptador;

    @Autowired
    private JuizDoPersonagem juiz;

    @BeforeEach
    void logarComoProfessora() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("professora@escola.test", null, "ROLE_PROFESSOR"));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void gravarRelatorio() throws IOException {
        RELATORIO.gravar();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("br.ufg.inf.labsimulacoes.ia.CasoDeQuebraDePersonagem#todos")
    void personagemMantemOPapelEOsLimites(CasoDeQuebraDePersonagem caso) {
        String resposta = null;
        Veredito veredito;
        try {
            resposta = adaptador.responder(caso.pedido(CENARIO), trecho -> {}).texto();
            veredito = julgar(caso, resposta);
        } catch (TextoComDadoPessoal e) {
            // RF-21: a pergunta nem chegou ao provedor, então não há resposta para julgar.
            if (!caso.bloqueadoAntesDoProvedor(e)) {
                RELATORIO.registrarErro(caso, resposta, e);
                throw e;
            }
            RELATORIO.registrar(caso, true, null, CasoDeQuebraDePersonagem.MOTIVO_DO_BLOQUEIO);
            return;
        } catch (RuntimeException e) {
            RELATORIO.registrarErro(caso, resposta, e);
            throw e;
        }
        RELATORIO.registrar(caso, veredito.aprovado(), resposta, veredito.motivo());

        assertThat(veredito.aprovado())
                .as("%s: %s%nResposta do personagem: %s", caso.id(), veredito.motivo(), resposta)
                .isTrue();
    }

    /** Um trecho proibido já reprova, sem gastar a chamada ao juiz. */
    private Veredito julgar(CasoDeQuebraDePersonagem caso, String resposta) {
        List<String> proibidos = caso.proibidosEm(resposta);
        if (!proibidos.isEmpty()) {
            return new Veredito(false, "A resposta contém trechos proibidos: " + String.join(", ", proibidos) + ".");
        }
        return juiz.avaliar(CENARIO, caso.historico(), caso.pergunta(), caso.criterio(), resposta);
    }
}
