package br.ufg.inf.labsimulacoes.acesso.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UsuarioTest {

    private static final String HASH = "$argon2id$hash";
    private static final Set<Perfil> PROFESSOR = Set.of(Perfil.PROFESSOR);

    @Test
    void usuarioNovoPodeEntrar() {
        Usuario usuario = Usuario.novo("Ana", "ana@escola.test", HASH, PROFESSOR);

        assertThat(usuario.ativo()).isTrue();
        assertThat(usuario.podeEntrar()).isTrue();
    }

    @Test
    void usuarioInativoNaoPodeEntrar() {
        assertThat(usuario(PROFESSOR).comAtivo(false).podeEntrar()).isFalse();
    }

    @Test
    void emailFicaNormalizado() {
        assertThat(Usuario.novo("Ana", "  Ana@Escola.TEST ", HASH, PROFESSOR).email()).isEqualTo("ana@escola.test");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void camposObrigatoriosPrecisamEstarPreenchidos(String vazio) {
        assertThatThrownBy(() -> Usuario.novo(vazio, "ana@escola.test", HASH, PROFESSOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Usuario.novo("Ana", vazio, HASH, PROFESSOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Usuario.novo("Ana", "ana@escola.test", vazio, PROFESSOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textoDoUsuarioNaoExpoeOHash() {
        assertThat(Usuario.novo("Ana", "ana@escola.test", HASH, PROFESSOR).toString()).doesNotContain(HASH);
    }

    @Test
    void usuarioPodeAcumularPerfis() {
        var perfis = Set.of(Perfil.PROFESSOR, Perfil.CURADOR);

        assertThat(usuario(PROFESSOR).comPerfis(perfis).perfis()).containsExactlyInAnyOrderElementsOf(perfis);
    }

    @Test
    void usuarioPrecisaTerAoMenosUmPerfil() {
        assertThatThrownBy(() -> Usuario.novo("Ana", "ana@escola.test", HASH, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> usuario(PROFESSOR).comPerfis(Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> usuario(PROFESSOR).comPerfis(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void soContaComoAdministradorAtivoQuemTemOPerfilEEstaAtivo() {
        Usuario administrador = usuario(Set.of(Perfil.ADMINISTRADOR));

        assertThat(administrador.ehAdministradorAtivo()).isTrue();
        assertThat(administrador.comAtivo(false).ehAdministradorAtivo()).isFalse();
        assertThat(usuario(PROFESSOR).ehAdministradorAtivo()).isFalse();
    }

    private static Usuario usuario(Set<Perfil> perfis) {
        return new Usuario(1L, "Ana", "ana@escola.test", HASH, true, perfis);
    }
}
