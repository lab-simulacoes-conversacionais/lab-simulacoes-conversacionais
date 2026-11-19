-- RNF-13: de onde veio cada consumo da IA, para o custo por sessão e o custo do dia.
-- A finalidade (FALA_EM_SALA, TESTE_DO_CURADOR ou SUGESTAO) e a referência (o id da sessão na fala em
-- sala, o da versão no teste do curador) são informadas por quem chama o adaptador; o módulo ia grava sem
-- saber o que significam e continua sem depender dos outros módulos. Sem CHECK nem FK: uma finalidade
-- nova não pode exigir trocar a restrição (RNF-12). As duas colunas aceitam nulo, porque a imagem
-- anterior não as preenche, e os metadados gravados antes desta migration ficam sem finalidade.
ALTER TABLE resposta_ia_meta
    ADD COLUMN finalidade    TEXT,
    ADD COLUMN referencia_id BIGINT;

-- O custo do dia soma os metadados por data de criação.
CREATE INDEX ix_resposta_ia_meta_criado_em ON resposta_ia_meta (criado_em);

-- O custo de uma sessão soma os metadados da finalidade e da referência.
CREATE INDEX ix_resposta_ia_meta_referencia ON resposta_ia_meta (finalidade, referencia_id);

-- RNF-13: um alerta ao administrador por dia em que o custo passou do limite. A chave primária garante
-- um só alerta por dia, mesmo com a conferência rodando de novo, depois de um reinício ou em duas
-- instâncias. O custo e o limite são os do momento do alerta; sem precisão fixa, porque o parâmetro do
-- administrador não tem teto.
CREATE TABLE alerta_custo_diario (
    dia       DATE        PRIMARY KEY,
    custo     NUMERIC     NOT NULL CHECK (custo >= 0),
    limite    NUMERIC     NOT NULL CHECK (limite > 0),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
