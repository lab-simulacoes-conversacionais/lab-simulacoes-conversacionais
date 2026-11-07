package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.acesso.dominio.Turma;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaDuplicada;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaEmUso;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaNaoEncontrada;
import br.ufg.inf.labsimulacoes.acesso.dominio.Turmas;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CadastroDeTurmasTest {

    private static final Turma OITAVO_A = new Turma(1L, "Escola", "8º ano", "Matutino", "8º A");
    private static final Turma OITAVO_B = new Turma(2L, "Escola", "8º ano", "Matutino", "8º B");

    @Mock
    private Turmas turmas;

    private CadastroDeTurmas cadastro;

    @BeforeEach
    void criarCadastro() {
        cadastro = new CadastroDeTurmas(turmas);
    }

    @Test
    void listarDevolveAsTurmasDaPorta() {
        when(turmas.listar()).thenReturn(List.of(OITAVO_A, OITAVO_B));

        assertThat(cadastro.listar()).containsExactly(OITAVO_A, OITAVO_B);
    }

    @Test
    void buscarDevolveATurma() {
        when(turmas.buscar(1L)).thenReturn(Optional.of(OITAVO_A));

        assertThat(cadastro.buscar(1L)).isEqualTo(OITAVO_A);
    }

    @Test
    void buscarTurmaInexistenteFalha() {
        when(turmas.buscar(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cadastro.buscar(9L)).isInstanceOf(TurmaNaoEncontrada.class);
    }

    @Test
    void cadastrarSalvaATurmaNova() {
        when(turmas.listar()).thenReturn(List.of(OITAVO_A));
        when(turmas.salvar(any())).thenAnswer(chamada -> comId(chamada.getArgument(0), 3L));

        Turma salva = cadastro.cadastrar(" Escola ", "8º ano", "Matutino", "8º C");

        assertThat(salva).isEqualTo(new Turma(3L, "Escola", "8º ano", "Matutino", "8º C"));
    }

    @Test
    void cadastrarTurmaComAMesmaIdentificacaoFalha() {
        when(turmas.listar()).thenReturn(List.of(OITAVO_A));

        assertThatThrownBy(() -> cadastro.cadastrar("Escola", "8º ano", "Matutino", " 8º A "))
                .isInstanceOf(TurmaDuplicada.class)
                .hasMessageContaining("8º A");
        verify(turmas, never()).salvar(any());
    }

    @Test
    void cadastrarComCampoVazioFalhaSemSalvar() {
        assertThatThrownBy(() -> cadastro.cadastrar("Escola", "8º ano", "Matutino", ""))
                .isInstanceOf(IllegalArgumentException.class);
        verify(turmas, never()).salvar(any());
    }

    @Test
    void alterarSalvaComOMesmoIdentificador() {
        when(turmas.buscar(1L)).thenReturn(Optional.of(OITAVO_A));
        when(turmas.listar()).thenReturn(List.of(OITAVO_A, OITAVO_B));
        when(turmas.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));

        Turma alterada = cadastro.alterar(1L, "Escola", "8º ano", "Vespertino", "8º A");

        assertThat(alterada).isEqualTo(new Turma(1L, "Escola", "8º ano", "Vespertino", "8º A"));
    }

    /** Salvar a turma sem mudar nada não pode ser confundido com duplicar ela mesma. */
    @Test
    void alterarSemMudarAIdentificacaoEPermitido() {
        when(turmas.buscar(1L)).thenReturn(Optional.of(OITAVO_A));
        when(turmas.listar()).thenReturn(List.of(OITAVO_A, OITAVO_B));
        when(turmas.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));

        assertThat(cadastro.alterar(1L, "Escola", "8º ano", "Matutino", "8º A")).isEqualTo(OITAVO_A);
    }

    @Test
    void alterarParaAIdentificacaoDeOutraTurmaFalha() {
        when(turmas.buscar(1L)).thenReturn(Optional.of(OITAVO_A));
        when(turmas.listar()).thenReturn(List.of(OITAVO_A, OITAVO_B));

        assertThatThrownBy(() -> cadastro.alterar(1L, "Escola", "8º ano", "Matutino", "8º B"))
                .isInstanceOf(TurmaDuplicada.class);
        verify(turmas, never()).salvar(any());
    }

    @Test
    void alterarTurmaInexistenteFalha() {
        when(turmas.buscar(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cadastro.alterar(9L, "Escola", "8º ano", "Matutino", "8º A"))
                .isInstanceOf(TurmaNaoEncontrada.class);
        verify(turmas, never()).salvar(any());
    }

    @Test
    void excluirApagaATurma() {
        when(turmas.buscar(1L)).thenReturn(Optional.of(OITAVO_A));

        cadastro.excluir(1L);

        verify(turmas).excluir(1L);
    }

    @Test
    void excluirTurmaInexistenteFalha() {
        when(turmas.buscar(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cadastro.excluir(9L)).isInstanceOf(TurmaNaoEncontrada.class);
        verify(turmas, never()).excluir(anyLong());
    }

    @Test
    void excluirTurmaComSessoesFalha() {
        when(turmas.buscar(1L)).thenReturn(Optional.of(OITAVO_A));
        doThrow(new TurmaEmUso(1L)).when(turmas).excluir(1L);

        assertThatThrownBy(() -> cadastro.excluir(1L)).isInstanceOf(TurmaEmUso.class);
    }

    private static Turma comId(Turma turma, long id) {
        return new Turma(id, turma.escola(), turma.anoSerie(), turma.turno(), turma.rotulo());
    }
}
