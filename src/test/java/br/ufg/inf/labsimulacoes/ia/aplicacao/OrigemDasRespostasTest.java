package br.ufg.inf.labsimulacoes.ia.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.ia.OrigemDaResposta;
import br.ufg.inf.labsimulacoes.ia.OrigemDasRespostas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A origem de uma resposta gravada, para a sinalização copiar (RF-23, RNF-10): só provedor, modelo e
 * versão do prompt, e só para quem sinaliza. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class OrigemDasRespostasTest {

    @Autowired
    private OrigemDasRespostas origens;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_PROFESSOR", "ROLE_CURADOR"})
    void quemSinalizaLeAOrigemDaResposta(String perfil) {
        long meta = jdbc.queryForObject(
                """
                INSERT INTO resposta_ia_meta (provedor, modelo, parametros, versao_prompt, tokens_entrada, custo)
                VALUES ('openai', 'gpt-6.1-terra', '{"max_tokens": 400}', 'personagem-v1', 316, 0.002)
                RETURNING id""",
                Long.class);
        entrar(perfil);

        assertThat(origens.daResposta(meta))
                .contains(new OrigemDaResposta("openai", "gpt-6.1-terra", "personagem-v1"));
    }

    @Test
    void metadadosQueNaoExistemNaoTemOrigem() {
        entrar("ROLE_PROFESSOR");

        assertThat(origens.daResposta(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void administradorNaoLeAOrigem() {
        entrar("ROLE_ADMINISTRADOR");

        assertThatThrownBy(() -> origens.daResposta(1)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void origemExigeOsTresCampos() {
        assertThatThrownBy(() -> new OrigemDaResposta(null, "m", "v1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OrigemDaResposta("openai", null, "v1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OrigemDaResposta("openai", "m", null)).isInstanceOf(NullPointerException.class);
    }

    private static void entrar(String perfil) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("pessoa@escola.test", null, perfil));
    }
}
