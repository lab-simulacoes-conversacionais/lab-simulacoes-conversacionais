package br.ufg.inf.labsimulacoes.cenarios;

import static br.ufg.inf.labsimulacoes.cenarios.Navegador.cod;
import static br.ufg.inf.labsimulacoes.cenarios.Navegador.local;
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
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * RF-21 e RN-13 no modo de teste do curador, pela tela: a pergunta com dado pessoal não chega ao
 * provedor, não cria pergunta de teste e não ocupa vaga do teto. O adaptador de IA é o real e só o
 * provedor é simulado; os dados são inventados. A configuração é a mesma dos outros testes de dados
 * pessoais, para o contexto ser um só.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lab.teste.contexto=dados-pessoais")
@Import(InfraestruturaDeTeste.class)
class DadosPessoaisNoTesteDoCenarioTest {

    private static final String SENHA = "uma senha de teste";
    private static final String HABILIDADE = "EF08HI96";
    private static final Pattern ID_NO_EDITOR = Pattern.compile("/cenarios/(\\d+)/editar");

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

    @BeforeEach
    void prepararCatalogo() {
        habilidades.gravar(List.of(new HabilidadeBncc(
                HABILIDADE, "Identificar as transformações tecnológicas do século XIX.", "EF", List.of(8), "História")));
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(delimiter = '|', textBlock = """
            Você conhece fulano@exemplo.com?          | e-mail   | fulano@exemplo.com
            Posso ligar no (62) 98765-4321?           | telefone | 98765-4321
            Meu CPF é 123.456.789-00, tudo bem?       | CPF      | 123.456.789-00
            Você morou na Rua das Flores, 123?        | endereço | Rua das Flores
            """)
    void perguntaComDadoPessoalNaoChegaAoProvedorNemOcupaVaga(String pergunta, String tipo, String dado)
            throws Exception {
        long id = enviado(logado(usuario(Perfil.PROFESSOR)));
        long casos = jdbc.queryForObject("SELECT count(*) FROM caso_bloqueado", Long.class);

        var resposta = logado(usuario(Perfil.CURADOR)).postForm("/curadoria/" + id + "/teste", "texto=" + cod(pergunta));

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body())
                .contains("A pergunta parece ter um dado pessoal (" + tipo + ").")
                .contains("Reescreva sem e-mail, telefone, CPF ou endereço, e não digite nomes de estudantes.")
                .contains("0/5 perguntas mínimas")
                .contains("0 de 40 perguntas usadas nesta rodada.")
                .doesNotContain(dado);
        verifyNoInteractions(provedor);
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM pergunta_de_teste p JOIN cenario_versao v ON v.id = p.cenario_versao_id
                        WHERE v.cenario_id = ?""", Long.class, id))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM caso_bloqueado", Long.class)).isEqualTo(casos);
    }

    private long enviado(Navegador autor) throws Exception {
        String titulo = "AAA Dados pessoais " + UUID.randomUUID();
        var resposta = autor.postForm("/cenarios", completo(titulo));
        var id = ID_NO_EDITOR.matcher(local(resposta));
        assertThat(id.find()).as("rascunho salvo").isTrue();
        long cenario = Long.parseLong(id.group(1));
        assertThat(local(autor.postForm("/cenarios/" + cenario, completo(titulo) + "&acao=enviar")))
                .endsWith("/cenarios/enviado");
        return cenario;
    }

    private static String completo(String titulo) {
        return "titulo=" + cod(titulo)
                + "&personagem=" + cod("Navegador português")
                + "&papelDaIa=" + cod("Conta a viagem em primeira pessoa")
                + "&contexto=" + cod("Lisboa, século XV")
                + "&objetivos=" + cod("Compreender as grandes navegações")
                + "&componente=" + cod("História")
                + "&ano=8"
                + "&habilidades=" + HABILIDADE
                + "&limitesDeAssunto=" + cod("Não inventar fatos")
                + "&fontes=" + cod("Livro didático da rede")
                + "&faixaEtaria=" + cod("13 a 14 anos")
                + "&duracaoPrevistaMin=15";
    }

    private String usuario(Perfil perfil) {
        String email = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Profissional", email, SENHA, Set.of(perfil));
        return email;
    }

    private Navegador logado(String email) throws Exception {
        return Navegador.logado(porta, email, SENHA);
    }
}
