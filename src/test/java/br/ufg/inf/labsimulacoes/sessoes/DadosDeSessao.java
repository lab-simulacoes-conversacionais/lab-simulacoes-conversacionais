package br.ufg.inf.labsimulacoes.sessoes;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Monta direto no banco o que uma sessão precisa: usuários, turma e uma versão de cenário aprovada.
 * Assim os testes de Sessões não dependem dos casos de uso internos de Cenários e Acesso.
 *
 * <p>Os nomes levam um sufixo aleatório, para os testes que confirmam a transação não colidirem.
 */
public final class DadosDeSessao {

    private final JdbcTemplate jdbc;

    public DadosDeSessao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Usuário com os perfis dados; devolve o e-mail, que é o nome do login nos testes. */
    public String usuario(String... perfis) {
        String email = UUID.randomUUID() + "@escola.test";
        long id = jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Nome', ?, 'hash') RETURNING id", Long.class, email);
        for (String perfil : perfis) {
            jdbc.update("INSERT INTO usuario_perfil (usuario_id, perfil) VALUES (?, ?)", id, perfil);
        }
        return email;
    }

    public long idDe(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
    }

    public long turma() {
        return jdbc.queryForObject(
                "INSERT INTO turma (escola, ano_serie, turno, rotulo) VALUES ('Escola', '8º ano', 'Manhã', ?) RETURNING id",
                Long.class,
                "8º " + UUID.randomUUID());
    }

    /** Cenário Aprovado com versão vigente, como a curadoria deixa (RN-01, RN-08). */
    public VersaoNoBanco cenarioAprovado(long autorId, long curadorId) {
        long cenarioId = cenario(autorId, "APROVADO");
        long versaoId = jdbc.queryForObject(
                """
                INSERT INTO cenario_versao (cenario_id, numero, status, titulo, conteudo, criado_por_id,
                                            aprovado_por_id, aprovado_em)
                VALUES (?, 1, 'APROVADA', 'O voo do 14-bis', '{}'::jsonb, ?, ?, now()) RETURNING id""",
                Long.class,
                cenarioId,
                autorId,
                curadorId);
        jdbc.update("UPDATE cenario SET versao_vigente_id = ? WHERE id = ?", versaoId, cenarioId);
        return new VersaoNoBanco(cenarioId, versaoId);
    }

    /** Cenário ainda em rascunho, que não pode ir para a sala (RN-01). */
    public long cenarioEmRascunho(long autorId) {
        long cenarioId = cenario(autorId, "RASCUNHO");
        jdbc.update(
                """
                INSERT INTO cenario_versao (cenario_id, numero, status, titulo, conteudo, criado_por_id)
                VALUES (?, 1, 'RASCUNHO', 'Rascunho', '{}'::jsonb, ?)""",
                cenarioId,
                autorId);
        return cenarioId;
    }

    /** Pergunta da turma ou resposta da IA, como o RF-16 vai gravar. */
    public void mensagem(long sessaoId, String autoria, String texto) {
        jdbc.update(
                "INSERT INTO mensagem (sessao_id, autoria, texto, criado_em) VALUES (?, ?, ?, clock_timestamp())",
                sessaoId,
                autoria,
                texto);
    }

    private long cenario(long autorId, String status) {
        return jdbc.queryForObject(
                "INSERT INTO cenario (autor_id, status) VALUES (?, ?) RETURNING id", Long.class, autorId, status);
    }

    public record VersaoNoBanco(long cenarioId, long versaoId) {}
}
