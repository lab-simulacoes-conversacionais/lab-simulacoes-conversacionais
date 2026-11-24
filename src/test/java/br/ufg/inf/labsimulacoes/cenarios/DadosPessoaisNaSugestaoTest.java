package br.ufg.inf.labsimulacoes.cenarios;

import static br.ufg.inf.labsimulacoes.cenarios.Navegador.cod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * RF-21 e RN-13 na sugestão de cenário, pela tela: o tema com dado pessoal não chega ao provedor, não
 * cria rascunho e não gasta a cota de sugestões (RNF-13). O adaptador de IA é o real e só o provedor é
 * simulado; os dados são inventados. A configuração é a mesma dos outros testes de dados pessoais, para
 * o contexto ser um só.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lab.teste.contexto=dados-pessoais")
@Import(InfraestruturaDeTeste.class)
class DadosPessoaisNaSugestaoTest {

    private static final String SENHA = "uma senha de teste";
    private static final String HABILIDADE = "EF08HI96";

    @LocalServerPort
    private int porta;

    @MockitoBean
    private ProvedorDeIa provedor;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void prepararCatalogo() {
        habilidades.gravar(List.of(new HabilidadeBncc(
                HABILIDADE, "Identificar as transformações tecnológicas do século XIX.", "EF", List.of(8), "História")));
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(delimiter = '|', textBlock = """
            O voo do 14-bis, dúvidas para fulano@exemplo.com  | e-mail   | fulano@exemplo.com
            O telégrafo, ligar para 98765-4321                | telefone | 98765-4321
            A Revolução Industrial, CPF 123.456.789-00        | CPF      | 123.456.789-00
            A feira da Rua das Flores, 123                    | endereço | Rua das Flores
            """)
    void temaComDadoPessoalNaoChegaAoProvedorNemGastaACota(String tema, String tipo, String dado) throws Exception {
        String email = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Profissional", email, SENHA, Set.of(Perfil.PROFESSOR));
        long id = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        var professora = Navegador.logado(porta, email, SENHA);

        var resposta = professora.postForm("/cenarios/sugestao",
                "tema=" + cod(tema) + "&componente=" + cod("História") + "&ano=8&habilidades=" + HABILIDADE);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body())
                .contains("O tema parece ter um dado pessoal (" + tipo + ").")
                .contains("Reescreva sem e-mail, telefone, CPF ou endereço, e não digite nomes de estudantes.")
                .containsPattern("<dialog[^>]*id=\"sugestao\"[^>]*open=\"open\"")
                .contains("value=\"" + HABILIDADE + "\"")
                .doesNotContain(dado);
        verifyNoInteractions(provedor);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cenario WHERE autor_id = ?", Long.class, id)).isZero();
        assertThat(redis.hasKey("sugestao:" + id + ":pedidos")).isFalse();
    }
}
