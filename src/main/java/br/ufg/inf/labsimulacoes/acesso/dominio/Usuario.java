package br.ufg.inf.labsimulacoes.acesso.dominio;

import java.util.Locale;
import java.util.Set;

/** Profissional da escola com acesso ao sistema. Estudantes não têm conta (RN-11). */
public record Usuario(Long id, String nome, String email, String senhaHash, boolean ativo, Set<Perfil> perfis) {

    public Usuario {
        exigirPreenchido(nome, "nome");
        exigirPreenchido(email, "e-mail");
        exigirPreenchido(senhaHash, "hash da senha");
        email = normalizarEmail(email);
        perfis = Set.copyOf(perfis);
    }

    public static Usuario novo(String nome, String email, String senhaHash, Set<Perfil> perfis) {
        exigirAoMenosUmPerfil(perfis);
        return new Usuario(null, nome, email, senhaHash, true, perfis);
    }

    /** O e-mail é único sem diferenciar maiúsculas, como no índice uq_usuario_email. */
    public static String normalizarEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean podeEntrar() {
        return ativo;
    }

    public boolean ehAdministradorAtivo() {
        return ativo && perfis.contains(Perfil.ADMINISTRADOR);
    }

    public Usuario comPerfis(Set<Perfil> novosPerfis) {
        exigirAoMenosUmPerfil(novosPerfis);
        return new Usuario(id, nome, email, senhaHash, ativo, novosPerfis);
    }

    public Usuario comAtivo(boolean novoAtivo) {
        return new Usuario(id, nome, email, senhaHash, novoAtivo, perfis);
    }

    /** Omite o hash, para que ele não vá parar em logs. */
    @Override
    public String toString() {
        return "Usuario[id=%d, email=%s, ativo=%s, perfis=%s]".formatted(id, email, ativo, perfis);
    }

    private static void exigirPreenchido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório");
        }
    }

    private static void exigirAoMenosUmPerfil(Set<Perfil> perfis) {
        if (perfis == null || perfis.isEmpty()) {
            throw new IllegalArgumentException("O usuário precisa ter ao menos um perfil");
        }
    }
}
