package br.ufg.inf.labsimulacoes.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Padrões de dado pessoal antes do envio ao provedor (RN-13, RNF-01), sem banco nem Spring. Todos os
 * dados são inventados: e-mails em exemplo.com e números que não pertencem a ninguém.
 */
class DetectorDeDadosPessoaisTest {

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            Escreva para fulano@exemplo.com depois da aula     | EMAIL
            FULANO.SILVA@EXEMPLO.COM.BR                        | EMAIL
            fulano arroba exemplo ponto com                    | EMAIL
            fulano (at) exemplo (dot) com                      | EMAIL
            fulano @ exemplo . com                             | EMAIL
            fulano (arroba) exemplo (ponto) com                | EMAIL
            fulano [arroba] exemplo.com                        | EMAIL
            (62) 98765-4321                                    | TELEFONE
            9-8765-4321                                        | TELEFONE
            (62) 9-8765-4321                                   | TELEFONE
            62 98765-4321                                      | TELEFONE
            +55 62 98765-4321                                  | TELEFONE
            +55 (62) 98765-4321                                | TELEFONE
            98765-4321                                         | TELEFONE
            9 8765-4321                                        | TELEFONE
            98765 4321                                         | TELEFONE
            98765.4321                                         | TELEFONE
            62987654321                                        | TELEFONE
            (62) 3555-0100                                     | TELEFONE
            (62)3555-0100                                      | TELEFONE
            +55 62 3555-0100                                   | TELEFONE
            6235550100                                         | TELEFONE
            o telefone dele é 3555-0100                        | TELEFONE
            whatsapp: 3555.0100                                | TELEFONE
            123.456.789-00                                     | CPF
            12345678900                                        | CPF
            123456789-00                                       | CPF
            123 456 789 00                                     | CPF
            123.456.789.00                                     | CPF
            123-456-789-00                                     | CPF
            12345-678                                          | ENDERECO
            CEP: 12.345-678                                    | ENDERECO
            cep 12345678                                       | ENDERECO
            Rua das Flores, 123                                | ENDERECO
            Av. Brasil, nº 1000                                | ENDERECO
            Travessa do Sol n° 45                              | ENDERECO
            Avenida Paulista, 900                              | ENDERECO
            Avenida Paulista, nº 1578                          | ENDERECO
            Rua 25 de Março, 120                               | ENDERECO
            Rua Dr. Fulano, 12                                 | ENDERECO
            Av. Pres. Vargas, 500                              | ENDERECO
            Rua Prof. Ana Lima, nº 40                          | ENDERECO
            Praça Mal. Deodoro, 7                              | ENDERECO
            Rua Cel. Pedro, apto 3                             | ENDERECO
            Rua das Flores, 123. Depois eu explico             | ENDERECO
            Rua T-25, Qd. 10, Lt. 5                            | ENDERECO
            Quadra 10 Lote 5                                   | ENDERECO
            ele mora na Rua das Flores, apto 12                | ENDERECO
            Rua das Flores, casa 3                             | ENDERECO
            Rua das Flores, Setor Bueno, bloco 2               | ENDERECO
            """)
    void padraoDeDadoPessoalEEncontrado(String texto, TipoDeDadoPessoal tipo) {
        assertThat(DadosPessoais.procurar(texto)).contains(tipo);
    }

    /** Textos comuns numa aula, que não podem ser bloqueados. */
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "Explique a habilidade EF06MA01",
        "Como trabalhar a EF69LP03?",
        "O que mudou entre 1914-1918?",
        "O que mudou entre 1914–1918?",
        "Compare os anos 2001-2010 com a década anterior",
        "A prova é em 26/11/2026",
        "O que aconteceu em 1822?",
        "Como era a vida no séc. XIX?",
        "Tirei nota 9,5 na prova",
        "Por que pi vale 3,14?",
        "A gravidade é 9,8 m/s²",
        "A viagem teve 100 km",
        "O ingresso custava R$ 1.500,00",
        "A população é de 12.325.232 pessoas",
        "Me ajude na questão 12",
        "Quem venceu a Guerra dos 100 Anos?",
        "Como voou o 14-bis?",
        "Quanto é 2^10 = 1024?",
        "O que havia na Avenida Paulista?",
        "Quem construiu a Praça dos Três Poderes?",
        "Para onde levava a Estrada Real?",
        "Por que a Rua 25 de Março tem esse nome?",
        "A Rua 25 de Março no século XIX era diferente?",
        "Rua 25 de Março, em São Paulo, é famosa",
        "Quantos km tem a Rodovia BR-153, km 12?",
        "O que é CEP?",
        "Leia https://pt.wikipedia.org/wiki/Rua_25_de_Março",
        "Onde fica -16.6869, -49.2648?",
        "Conte 1 2 3 4 5 6 7 8",
        "Ele mora na rua das Flores",
        "Quem inventou o arroba?",
        "Siga @santosdumont",
        "O telefone foi inventado em 1876",
        "O telefone, entre 1939-1945, mudou a guerra?",
        // Os blocos com separador não juntam um número maior (item 4).
        "Os números 123 456 789 0001 estão na tabela",
        "O código 1234 567 890 12 é do livro",
        "Ligue os pontos 9-8765-43210",
        // Falsos positivos da revisão (item 5).
        "Siga @santosdumont. Ele voou em 1906",
        "Assisti à TV. Em 1950, 30% das casas tinham rádio",
        "A rua principal, 1920 foi asfaltada",
        "Na estrada, a casa 3 pegou fogo",
        "Segundo R. Silva, 2003, o voo durou pouco",
        // Abreviação com ponto só no começo do nome: no meio, "min." atravessaria a frase.
        "A rua fica a 10 min. Em 1950, 30 casas tinham rádio",
        "A rua fica a 10 min. Depois, 30 casas"
    })
    void textoDeAulaNaoEBloqueado(String texto) {
        assertThat(DadosPessoais.procurar(texto)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void textoVazioNaoTemDadoPessoal(String texto) {
        assertThat(DadosPessoais.procurar(texto)).isEmpty();
    }

    @Test
    void normalizacaoTiraAcentosCaixaInvisiveisEEspacos() {
        assertThat(DadosPessoais.normalizar("  AVENIDA  Goiás, Nº​ 100 ")).isEqualTo("avenida goias, n° 100");
        assertThat(DadosPessoais.normalizar("９８７６５－４３２１")).isEqualTo("98765-4321");
    }

    @Test
    void normalizacaoDoEmailJuntaArrobaEPonto() {
        assertThat(DadosPessoais.paraEmail("fulano arroba exemplo ponto com ponto br"))
                .isEqualTo("fulano@exemplo.com.br");
        assertThat(DadosPessoais.paraEmail("fulano [at] exemplo [dot] com")).isEqualTo("fulano@exemplo.com");
    }

    /** Acentos, maiúsculas, espaços estranhos e caracteres invisíveis não escondem o dado. */
    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("disfarcados")
    void dadoDisfarcadoAindaEEncontrado(String texto, TipoDeDadoPessoal tipo) {
        assertThat(DadosPessoais.procurar(texto)).contains(tipo);
    }

    static Stream<Arguments> disfarcados() {
        return Stream.of(
                Arguments.of("98765​-4321", TipoDeDadoPessoal.TELEFONE),
                Arguments.of("98765 4321", TipoDeDadoPessoal.TELEFONE),
                Arguments.of("９８７６５－４３２１", TipoDeDadoPessoal.TELEFONE),
                Arguments.of("FULANO@EXEMPLO.COM", TipoDeDadoPessoal.EMAIL),
                Arguments.of("Fulano ARROBA exemplo PONTO com", TipoDeDadoPessoal.EMAIL),
                Arguments.of("PRAÇA DA SÉ, Nº 10", TipoDeDadoPessoal.ENDERECO),
                Arguments.of("123​.456.789-00", TipoDeDadoPessoal.CPF),
                // Meia-risca (U+2013) e sinal de menos (U+2212), comuns em texto colado de editores.
                Arguments.of("98765–4321", TipoDeDadoPessoal.TELEFONE),
                Arguments.of("(62) 98765–4321", TipoDeDadoPessoal.TELEFONE),
                Arguments.of("98765−4321", TipoDeDadoPessoal.TELEFONE),
                Arguments.of("123.456.789–00", TipoDeDadoPessoal.CPF));
    }

    /** O tempo cresce em linha com o texto: nenhuma entrada maliciosa trava a verificação (sem ReDoS). */
    @ParameterizedTest
    @MethodSource("maliciosos")
    void textoMaliciosoLongoTerminaRapido(String texto) {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertThat(DadosPessoais.procurar(texto)).isEmpty());
    }

    static Stream<String> maliciosos() {
        return Stream.of(
                "a".repeat(10_000) + "@",
                "a@a".repeat(3_400),
                "9".repeat(10_000),
                "1-".repeat(5_000),
                "rua ".repeat(2_500),
                "rua das flores ".repeat(700),
                "rua dr. ".repeat(1_250),
                "av. pres. dr. prof. ".repeat(500),
                "tel ".repeat(2_500),
                "qd 1 ".repeat(2_000),
                "arroba ponto ".repeat(800),
                "(".repeat(10_000));
    }
}
