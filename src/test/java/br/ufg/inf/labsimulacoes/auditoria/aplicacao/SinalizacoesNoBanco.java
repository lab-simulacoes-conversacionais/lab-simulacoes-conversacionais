package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Monta direto no banco sinalizações pendentes de uma sessão, para os testes da triagem não dependerem do
 * caso de uso da sinalização nem do prazo do professor. Cada sinalização tem a própria resposta. Os textos
 * são inventados.
 */
final class SinalizacoesNoBanco {

    private static final Instant INICIO = Instant.parse("2026-11-20T13:00:00Z");

    private final JdbcTemplate jdbc;
    final long professorId;
    final long versaoId;
    private final long sessaoId;

    SinalizacoesNoBanco(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        var dados = new DadosDeSessao(jdbc);
        professorId = dados.idDe(dados.usuario("PROFESSOR"));
        versaoId = dados.cenarioAprovado(professorId, dados.idDe(dados.usuario("CURADOR"))).versaoId();
        sessaoId = jdbc.queryForObject(
                """
                INSERT INTO sessao (professor_id, turma_id, cenario_versao_id, limite_perguntas, status, iniciada_em,
                                    encerrada_em)
                VALUES (?, ?, ?, 40, 'ENCERRADA', ?, ?) RETURNING id""",
                Long.class, professorId, dados.turma(), versaoId, INICIO.atOffset(ZoneOffset.UTC),
                INICIO.plusSeconds(3000).atOffset(ZoneOffset.UTC));
    }

    /** Uma sinalização pendente do professor, com gravidade percebida grave. */
    long pendente(Instant criadaEm) {
        return pendente(criadaEm, "GRAVE", "PROFESSOR");
    }

    long pendente(Instant criadaEm, String gravidadePercebida, String perfil) {
        long mensagem = jdbc.queryForObject(
                "INSERT INTO mensagem (sessao_id, autoria, texto, criado_em) VALUES (?, 'IA', ?, ?) RETURNING id",
                Long.class, sessaoId, "Voei em 1901, em Paris.", INICIO.plusSeconds(60).atOffset(ZoneOffset.UTC));
        return jdbc.queryForObject(
                """
                INSERT INTO sinalizacao (sessao_id, sessao_iniciada_em, sessao_professor_id, cenario_versao_id,
                                         mensagem_id, pergunta, resposta, respondida_em, tipo, gravidade_percebida,
                                         justificativa, sinalizado_por_id, perfil, criada_em)
                VALUES (?, ?, ?, ?, ?, 'Quando foi o primeiro voo?', 'Voei em 1901, em Paris.', ?, 'ERRO_HISTORICO',
                        ?, 'O voo homologado foi em 1906.', ?, ?, ?)
                RETURNING id""",
                Long.class, sessaoId, INICIO.atOffset(ZoneOffset.UTC), professorId, versaoId, mensagem,
                INICIO.plusSeconds(60).atOffset(ZoneOffset.UTC), gravidadePercebida, professorId, perfil,
                criadaEm.atOffset(ZoneOffset.UTC));
    }
}
