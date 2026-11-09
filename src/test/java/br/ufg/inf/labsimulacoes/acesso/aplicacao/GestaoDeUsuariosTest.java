package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuario;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuarios;
import br.ufg.inf.labsimulacoes.acesso.infraestrutura.PublicacaoDeEventos;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Regras da gestão de usuários, sem banco. A verificação do perfil fica no {@code PerfisTest}. */
class GestaoDeUsuariosTest {

    private static final long ID_ADMIN = 1L;
    private static final long ID_ALVO = 2L;
    private static final Set<Perfil> ADMIN = Set.of(Perfil.ADMINISTRADOR);
    private static final Set<Perfil> PROFESSOR = Set.of(Perfil.PROFESSOR);

    private final Usuarios usuarios = mock(Usuarios.class);
    private final CadastroDeUsuarios cadastro = mock(CadastroDeUsuarios.class);
    private final PublicacaoDeEventos eventos = mock(PublicacaoDeEventos.class);
    private final UsuarioLogado usuarioLogado = mock(UsuarioLogado.class);
    private final GestaoDeUsuarios gestao = new GestaoDeUsuarios(usuarios, cadastro, eventos, usuarioLogado);

    @BeforeEach
    void logarComoAdministrador() {
        when(usuarioLogado.id()).thenReturn(ID_ADMIN);
        when(usuarios.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void cadastrarPublicaOsPerfisDoNovoUsuarioComOAutor() {
        var novo = new Usuario(ID_ALVO, "Ana", "ana@escola.test", "hash", true, PROFESSOR);
        when(cadastro.cadastrar("Ana", "ana@escola.test", "senha", PROFESSOR)).thenReturn(novo);

        assertThat(gestao.cadastrar("Ana", "ana@escola.test", "senha", PROFESSOR)).isEqualTo(novo);

        verify(eventos).perfilAlterado(ID_ALVO, ID_ADMIN, Set.of(), PROFESSOR);
    }

    @Test
    void atribuirPerfisSalvaEPublicaAntesEDepois() {
        alvo(true, PROFESSOR);
        var novos = Set.of(Perfil.PROFESSOR, Perfil.CURADOR);

        assertThat(gestao.atribuirPerfis(ID_ALVO, novos).perfis()).containsExactlyInAnyOrderElementsOf(novos);

        verify(eventos).perfilAlterado(ID_ALVO, ID_ADMIN, PROFESSOR, novos);
    }

    @Test
    void atribuirOsMesmosPerfisNaoGeraRegistroNoLog() {
        alvo(true, PROFESSOR);

        gestao.atribuirPerfis(ID_ALVO, PROFESSOR);

        verify(usuarios, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void atribuirPerfisExigeAoMenosUmPerfil() {
        alvo(true, PROFESSOR);

        assertThatThrownBy(() -> gestao.atribuirPerfis(ID_ALVO, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void naoRemovePerfilDoUltimoAdministrador() {
        alvo(true, ADMIN);
        when(usuarios.contarAdministradoresAtivos()).thenReturn(1L);

        assertThatThrownBy(() -> gestao.atribuirPerfis(ID_ALVO, PROFESSOR))
                .isInstanceOf(IllegalStateException.class);

        verify(usuarios, never()).salvar(any());
        verify(eventos, never()).perfilAlterado(anyLong(), anyLong(), any(), any());
    }

    @Test
    void naoDesativaOUltimoAdministrador() {
        alvo(true, ADMIN);
        when(usuarios.contarAdministradoresAtivos()).thenReturn(1L);

        assertThatThrownBy(() -> gestao.desativar(ID_ALVO)).isInstanceOf(IllegalStateException.class);

        verify(usuarios, never()).salvar(any());
    }

    @Test
    void administradorPodeSairSeHouverOutro() {
        alvo(true, ADMIN);
        when(usuarios.contarAdministradoresAtivos()).thenReturn(2L);

        assertThat(gestao.desativar(ID_ALVO).ativo()).isFalse();
        assertThat(gestao.atribuirPerfis(ID_ALVO, PROFESSOR).perfis()).isEqualTo(PROFESSOR);
    }

    @Test
    void administradorInativoNaoContaComoUltimo() {
        alvo(false, ADMIN);
        when(usuarios.contarAdministradoresAtivos()).thenReturn(1L);

        assertThat(gestao.atribuirPerfis(ID_ALVO, PROFESSOR).perfis()).isEqualTo(PROFESSOR);
    }

    @Test
    void desativarUsuarioComumNaoDependeDeAdministradores() {
        alvo(true, PROFESSOR);

        assertThat(gestao.desativar(ID_ALVO).ativo()).isFalse();
    }

    @Test
    void ativarDevolveOAcesso() {
        alvo(false, PROFESSOR);

        assertThat(gestao.ativar(ID_ALVO).ativo()).isTrue();
    }

    @Test
    void usuarioInexistenteERecusado() {
        when(usuarios.buscarPorId(ID_ALVO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gestao.buscar(ID_ALVO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gestao.ativar(ID_ALVO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listarEBuscarConsultamOsUsuarios() {
        Usuario usuario = alvo(true, PROFESSOR);
        when(usuarios.listar()).thenReturn(List.of(usuario));

        assertThat(gestao.listar()).containsExactly(usuario);
        assertThat(gestao.buscar(ID_ALVO)).isEqualTo(usuario);
    }

    private Usuario alvo(boolean ativo, Set<Perfil> perfis) {
        var usuario = new Usuario(ID_ALVO, "Ana", "ana@escola.test", "hash", ativo, perfis);
        when(usuarios.buscarPorId(ID_ALVO)).thenReturn(Optional.of(usuario));
        return usuario;
    }
}
