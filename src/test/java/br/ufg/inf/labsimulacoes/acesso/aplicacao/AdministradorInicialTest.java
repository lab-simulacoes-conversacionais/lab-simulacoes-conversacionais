package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuario;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuarios;
import br.ufg.inf.labsimulacoes.acesso.infraestrutura.PublicacaoDeEventos;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdministradorInicialTest {

    private static final String EMAIL = "admin@escola.test";
    private static final Set<Perfil> ADMIN = Set.of(Perfil.ADMINISTRADOR);

    private final Usuarios usuarios = mock(Usuarios.class);
    private final CadastroDeUsuarios cadastro = mock(CadastroDeUsuarios.class);
    private final PublicacaoDeEventos eventos = mock(PublicacaoDeEventos.class);

    @Test
    void semAdministradorCriaOPrimeiroERegistraNoLog() {
        when(usuarios.buscarPorEmail(EMAIL)).thenReturn(Optional.empty());
        when(cadastro.cadastrar("Administrador", EMAIL, "senha", ADMIN))
                .thenReturn(new Usuario(7L, "Administrador", EMAIL, "hash", true, ADMIN));

        inicial(EMAIL, "senha").run(null);

        verify(eventos).perfilAlterado(7L, 7L, Set.of(), ADMIN);
    }

    @Test
    void comAdministradorAtivoNaoFazNada() {
        when(usuarios.contarAdministradoresAtivos()).thenReturn(1L);

        inicial(EMAIL, "senha").run(null);

        verifyNoInteractions(cadastro, eventos);
    }

    @Test
    void semAsVariaveisNaoCriaNinguem() {
        inicial("", "senha").run(null);
        inicial(EMAIL, " ").run(null);

        verifyNoInteractions(cadastro, eventos);
    }

    @Test
    void emailDeOutroUsuarioNaoImpedeAAplicacaoDeSubirNemGanhaOPerfil() {
        when(usuarios.buscarPorEmail(anyString())).thenReturn(Optional.of(
                new Usuario(3L, "Ana", EMAIL, "hash", true, Set.of(Perfil.PROFESSOR))));

        inicial(EMAIL, "senha").run(null);

        verifyNoInteractions(cadastro, eventos);
        verify(usuarios, org.mockito.Mockito.never()).salvar(any());
    }

    private AdministradorInicial inicial(String email, String senha) {
        return new AdministradorInicial(usuarios, cadastro, eventos, email, senha);
    }
}
