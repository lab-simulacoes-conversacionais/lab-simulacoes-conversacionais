package br.ufg.inf.labsimulacoes.auditoria.infraestrutura;

import br.ufg.inf.labsimulacoes.auditoria.dominio.CasoBloqueado;
import br.ufg.inf.labsimulacoes.auditoria.dominio.CasosBloqueados;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabela caso_bloqueado com SQL direto, como o log de auditoria. */
@Repository
class CasosBloqueadosJdbc implements CasosBloqueados {

    private static final String INSERIR = """
            INSERT INTO caso_bloqueado (sessao_id, cenario_versao_id, origem, pergunta, trecho_bloqueado, categorias,
                                        resposta_ia_meta_id, status, ocorrido_em)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT uq_caso_bloqueado_ocorrencia DO NOTHING""";

    private final JdbcTemplate jdbc;

    CasosBloqueadosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean abrir(CasoBloqueado caso) {
        return jdbc.update(conexao -> {
                    PreparedStatement comando = conexao.prepareStatement(INSERIR);
                    comando.setLong(1, caso.sessaoId());
                    comando.setLong(2, caso.versaoId());
                    comando.setString(3, caso.origem().name());
                    comando.setString(4, caso.pergunta());
                    comando.setString(5, caso.trechoBloqueado());
                    comando.setArray(6, conexao.createArrayOf("text", caso.categorias().toArray()));
                    if (caso.respostaIaMetaId() == null) {
                        comando.setNull(7, Types.BIGINT);
                    } else {
                        comando.setLong(7, caso.respostaIaMetaId());
                    }
                    comando.setString(8, CasoBloqueado.ABERTO);
                    comando.setObject(9, caso.ocorridoEm().atOffset(ZoneOffset.UTC));
                    return comando;
                })
                == 1;
    }
}
