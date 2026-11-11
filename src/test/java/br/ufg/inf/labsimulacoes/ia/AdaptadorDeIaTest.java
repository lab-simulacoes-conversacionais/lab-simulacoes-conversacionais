package br.ufg.inf.labsimulacoes.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

/**
 * Porta do adaptador de ponta a ponta, com o modelo da OpenAI simulado e PostgreSQL real: cada resposta
 * gerada deixa os metadados em resposta_ia_meta (RNF-10) e só professor e curador usam a porta (RN-37).
 * Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class AdaptadorDeIaTest {

    private static final PedidoDeFala PEDIDO = new PedidoDeFala(
            CenariosDeTeste.VOO_DO_14_BIS,
            List.of(),
            "Quem é você?");

    @MockitoBean
    private ChatModel modelo;

    @MockitoBean
    private ModerationModel moderacao;

    @Autowired
    private AdaptadorDeIa adaptador;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void simularModelo() {
        when(modelo.getDefaultOptions()).thenReturn(
                OpenAiChatOptions.builder().model("gpt-6.1-terra").reasoningEffort("low").build());
        when(modelo.stream(any(Prompt.class))).thenReturn(Flux.just(
                resposta("Sou Santos Dumont, ", null),
                resposta("um personagem simulado por IA.", new DefaultUsage(316, 97))));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void respostaDoProfessorGravaOsMetadadosNoBanco() {
        logar("ROLE_PROFESSOR");
        List<String> trechos = new ArrayList<>();

        RespostaDoPersonagem resposta = adaptador.responder(PEDIDO, trechos::add);

        assertThat(trechos).containsExactly("Sou Santos Dumont, ", "um personagem simulado por IA.");
        assertThat(resposta.texto()).isEqualTo("Sou Santos Dumont, um personagem simulado por IA.");
        Map<String, Object> linha = jdbc.queryForMap("""
                SELECT provedor, modelo, parametros->>'reasoning_effort' AS esforco, versao_prompt,
                       tokens_entrada, tokens_saida, custo, latencia_ms
                FROM resposta_ia_meta WHERE id = ?""", resposta.metadadosId());
        assertThat(linha)
                .containsEntry("provedor", "openai")
                .containsEntry("modelo", "gpt-6.1-terra")
                .containsEntry("esforco", "low")
                .containsEntry("versao_prompt", "personagem-v1")
                .containsEntry("tokens_entrada", 316)
                .containsEntry("tokens_saida", 97);
        assertThat((BigDecimal) linha.get("custo")).isEqualByComparingTo("0.000801");
        assertThat((Integer) linha.get("latencia_ms")).isNotNegative();
    }

    @Test
    void curadorTambemUsaAPorta() {
        logar("ROLE_CURADOR");

        assertThat(adaptador.responder(PEDIDO, trecho -> {}).metadadosId()).isPositive();
    }

    @Test
    void administradorNaoUsaAPorta() {
        logar("ROLE_ADMINISTRADOR");

        assertThatThrownBy(() -> adaptador.responder(PEDIDO, trecho -> {})).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adaptador.moderar("Oi")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adaptador.sugerirCenario(new PedidoDeSugestao("Voo", null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semLoginNaoUsaAPorta() {
        assertThatThrownBy(() -> adaptador.responder(PEDIDO, trecho -> {}))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private static ChatResponse resposta(String texto, DefaultUsage uso) {
        var metadados = ChatResponseMetadata.builder().model("gpt-6.1-terra");
        if (uso != null) {
            metadados.usage(uso);
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(texto))), metadados.build());
    }

    private static void logar(String perfil) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("pessoa@escola.test", null, perfil));
    }
}
