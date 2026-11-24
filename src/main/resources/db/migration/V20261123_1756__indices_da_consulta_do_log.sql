-- A consulta do log (RF-26) ordena por (ocorrido_em DESC, id DESC) e pagina por cursor nessa mesma
-- ordem. Os índices compostos entregam as linhas já ordenadas, com e sem o filtro de usuário. Os
-- índices de uma coluna só continuam, porque as migrations só adicionam estruturas (RNF-12).
CREATE INDEX ix_log_auditoria_ocorrido_em_id ON log_auditoria (ocorrido_em, id);
CREATE INDEX ix_log_auditoria_usuario_ocorrido_em_id ON log_auditoria (usuario_id, ocorrido_em, id);
