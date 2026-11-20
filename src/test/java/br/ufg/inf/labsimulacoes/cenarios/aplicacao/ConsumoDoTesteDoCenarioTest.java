package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudosDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.ia.ConsumoDaIa;
import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.ia.ResumoDoConsumo;
import br.ufg.inf.labsimulacoes.ia.dominio.Geracao;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.dominio.UsoDoModelo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * RNF-13: o consumo do teste do curador fica ligado à versão testada, com a finalidade própria, no banco
 * e no Micrometer. Com o adaptador de IA real e só o provedor simulado. Cada teste desfaz o que gravou.
 */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
@Transactional
class ConsumoDoTesteDoCenarioTest {

    @MockitoBean
    private ProvedorDeIa provedor;

    @Autowired
    private TesteDoCenario teste;

    @Autowired
    private AutoriaDeCenarios autoria;

    @Autowired
    private EnvioACuradoria envio;

    @Autowired
    private Habilidades habilidades;

    @Autowired
    private ConsumoDaIa consumo;

    @Autowired
    private MeterRegistry metricas;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void perguntaDeTesteGravaOConsumoComAFinalidadeEAVersao() {
        String autora = inserirUsuario();
        String curadora = inserirUsuario();
        habilidades.gravar(List.of(new HabilidadeBncc("EF08HI03", "Descrição", "EF", List.of(8), "História")));
        logar(autora, "ROLE_PROFESSOR");
        long cenario = autoria.criar(ConteudosDeTeste.completo()).id();
        long versao = envio.enviar(cenario).versao().id();
        logar(curadora, "ROLE_CURADOR");
        when(provedor.nome()).thenReturn("openai");
        when(provedor.moderar(anyString())).thenReturn(ResultadoDaModeracao.aprovado());
        when(provedor.conversar(any(), any())).thenReturn(
                new Geracao<>("Sou Santos Dumont.", "gpt-6.1-terra", Map.of(), new UsoDoModelo(100, 10)));
        double antes = custoNoMicrometer();

        teste.perguntar(cenario, "Quem é você?");

        ResumoDoConsumo resumo = consumo.daReferencia(FinalidadeDoConsumo.TESTE_DO_CURADOR, versao);
        assertThat(resumo.respostas()).isEqualTo(1);
        assertThat(resumo.tokensEntrada()).isEqualTo(100);
        assertThat(resumo.tokensSaida()).isEqualTo(10);
        assertThat(resumo.custo()).isPositive();
        assertThat(consumo.daReferencia(FinalidadeDoConsumo.FALA_EM_SALA, versao).respostas()).isZero();
        assertThat(custoNoMicrometer() - antes).isEqualTo(resumo.custo().doubleValue(), org.assertj.core.data.Offset.offset(1e-9));
    }

    private double custoNoMicrometer() {
        Counter contador = metricas.find("lab.ia.custo").tag("finalidade", "teste-do-curador").counter();
        return contador == null ? 0 : contador.count();
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
