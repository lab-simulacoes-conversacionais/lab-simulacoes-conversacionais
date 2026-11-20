package br.ufg.inf.labsimulacoes.metricas.apresentacao;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Aba Consumo e aviso de custo da administração (RNF-13, RN-37), por MockMvc com a segurança real. O
 * login vai na sessão HTTP, e os perfis são os do banco, como confere a sincronização de perfis a cada
 * requisição. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class ConsumoPelaTelaTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter seguranca;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    /** Sem o @AutoConfigureMockMvc, que criaria outro contexto, com outros contêineres, só para este teste. */
    @BeforeEach
    void montarMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(seguranca).build();
    }

    @Test
    void administradorVeOConsumoDeHojeEOLimite() throws Exception {
        jdbc.update("""
                INSERT INTO parametro (chave, valor) VALUES ('custo_diario_maximo_usd', '5')
                ON CONFLICT (chave) DO UPDATE SET valor = excluded.valor""");

        mvc.perform(get("/admin/consumo").session(logado("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Consumo da IA hoje")))
                .andExpect(content().string(containsString("Custo diário máximo")))
                .andExpect(content().string(containsString("US$ 5,00")))
                .andExpect(content().string(containsString("sem custo informado")))
                .andExpect(content().string(containsString("href=\"/admin/consumo\" aria-current=\"page\"")));
    }

    /** "Custo por sessão consultável": o administrador busca a sessão pelo id. */
    @Test
    void administradorConsultaOCustoDeUmaSessao() throws Exception {
        long sessao = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
        jdbc.update("""
                INSERT INTO resposta_ia_meta (provedor, modelo, versao_prompt, tokens_entrada, tokens_saida, custo,
                                              finalidade, referencia_id)
                VALUES ('openai', 'm', 'personagem-v1', 100, 10, 0.0003, 'FALA_EM_SALA', ?),
                       ('openai', 'm', 'personagem-v1', NULL, NULL, NULL, 'FALA_EM_SALA', ?)""", sessao, sessao);

        mvc.perform(get("/admin/consumo").param("sessao", Long.toString(sessao)).session(logado("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Custo da sessão " + sessao)))
                .andExpect(content().string(containsString("US$ 0,0003")))
                .andExpect(content().string(containsString("2 respostas, 1 sem custo informado")))
                .andExpect(content().string(containsString("100 de entrada e 10 de saída")));
    }

    @Test
    void professorNaoAbreOConsumo() throws Exception {
        mvc.perform(get("/admin/consumo").session(logado("PROFESSOR"))).andExpect(status().isForbidden());
    }

    @Test
    void curadorNaoAbreOConsumo() throws Exception {
        mvc.perform(get("/admin/consumo").session(logado("CURADOR"))).andExpect(status().isForbidden());
    }

    /** Professor e curador juntos também não: o consumo é só do administrador (RN-37). */
    @Test
    void professorECuradorJuntosNaoAbremOConsumo() throws Exception {
        mvc.perform(get("/admin/consumo").session(logado("PROFESSOR", "CURADOR"))).andExpect(status().isForbidden());
    }

    /** Enquanto houver alerta hoje, todas as abas da administração mostram o aviso no topo. */
    @Test
    void alertaDeHojeApareceEmTodasAsAbasDaAdministracao() throws Exception {
        jdbc.update("INSERT INTO alerta_custo_diario (dia, custo, limite) VALUES (?, 0.02, 0.01)", hoje());
        MockHttpSession admin = logado("ADMINISTRADOR");

        for (String aba : new String[] {"/admin/consumo", "/admin/parametros", "/admin/usuarios", "/admin/turmas"}) {
            mvc.perform(get(aba).session(admin))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("O custo da IA hoje passou do custo diário máximo")))
                    .andExpect(content().string(containsString("de US$ 0,01.")));
        }
    }

    @Test
    void semAlertaHojeNaoHaAviso() throws Exception {
        jdbc.update("INSERT INTO alerta_custo_diario (dia, custo, limite) VALUES (?, 0.02, 0.01)", hoje().minusDays(1));

        mvc.perform(get("/admin/parametros").session(logado("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passou do custo diário máximo"))));
    }

    private static LocalDate hoje() {
        return LocalDate.now(FUSO);
    }

    /** Usuário novo no banco com os perfis, e a sessão HTTP já autenticada com eles. */
    private MockHttpSession logado(String... perfis) {
        String email = UUID.randomUUID() + "@escola.test";
        long id = jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
        for (String perfil : perfis) {
            jdbc.update("INSERT INTO usuario_perfil (usuario_id, perfil) VALUES (?, ?)", id, perfil);
        }
        var autenticacao = UsernamePasswordAuthenticationToken.authenticated(
                email, null, Arrays.stream(perfis).map(p -> new SimpleGrantedAuthority("ROLE_" + p)).toList());
        var sessao = new MockHttpSession();
        sessao.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(autenticacao));
        return sessao;
    }
}
