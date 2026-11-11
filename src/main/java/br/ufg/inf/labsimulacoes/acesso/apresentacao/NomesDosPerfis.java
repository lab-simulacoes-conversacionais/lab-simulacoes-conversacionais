package br.ufg.inf.labsimulacoes.acesso.apresentacao;

import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import java.util.List;
import java.util.stream.Stream;

/**
 * Nome e descrição de cada perfil como aparecem nas telas (RF-02), num só lugar para a lista de
 * usuários, o formulário e a confirmação usarem os mesmos textos do protótipo.
 */
final class NomesDosPerfis {

    /** Um perfil como opção de marcar nas telas. */
    record Opcao(Perfil valor, String nome, String descricao) {}

    private NomesDosPerfis() {}

    static List<Opcao> opcoes() {
        return Stream.of(Perfil.values()).map(p -> new Opcao(p, de(p), descricao(p))).toList();
    }

    static String de(Perfil perfil) {
        return switch (perfil) {
            case PROFESSOR -> "Professor";
            case CURADOR -> "Curador Pedagógico";
            case ADMINISTRADOR -> "Administrador";
        };
    }

    private static String descricao(Perfil perfil) {
        return switch (perfil) {
            case PROFESSOR -> "Conduz sessões e cria cenários.";
            case CURADOR -> "Revisa cenários e sinalizações.";
            case ADMINISTRADOR -> "Gerencia usuários e parâmetros.";
        };
    }
}
