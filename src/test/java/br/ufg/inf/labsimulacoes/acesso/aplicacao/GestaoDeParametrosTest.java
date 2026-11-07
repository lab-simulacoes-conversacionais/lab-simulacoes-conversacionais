package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.acesso.dominio.Parametros;
import br.ufg.inf.labsimulacoes.acesso.dominio.RegistroDeParametros;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GestaoDeParametrosTest {

    @Mock
    private RegistroDeParametros registro;

    @InjectMocks
    private GestaoDeParametros gestao;

    @InjectMocks
    private ConsultaDeParametros consulta;

    @Test
    void consultarDevolveOsParametrosGravados() {
        when(registro.carregar()).thenReturn(new Parametros(30, BigDecimal.TEN));

        assertThat(gestao.consultar()).isEqualTo(new Parametros(30, BigDecimal.TEN));
    }

    @Test
    void alterarGravaOsNovosParametros() {
        Parametros alterados = gestao.alterar(25, new BigDecimal("4.50"));

        assertThat(alterados).isEqualTo(new Parametros(25, new BigDecimal("4.5")));
        verify(registro).salvar(alterados);
    }

    @Test
    void alterarSemCustoDeixaSemCustoMaximo() {
        gestao.alterar(25, null);

        verify(registro).salvar(new Parametros(25, null));
    }

    @Test
    void alterarComValorInvalidoNaoGrava() {
        assertThatThrownBy(() -> gestao.alterar(0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gestao.alterar(40, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        verify(registro, never()).salvar(any());
    }

    @Test
    void consultaDosOutrosModulosDevolveOsValoresGravados() {
        when(registro.carregar()).thenReturn(new Parametros(30, BigDecimal.TEN));

        assertThat(consulta.limiteDePerguntasPorSessao()).isEqualTo(30);
        assertThat(consulta.custoDiarioMaximoUsd()).contains(BigDecimal.TEN);
    }
}
