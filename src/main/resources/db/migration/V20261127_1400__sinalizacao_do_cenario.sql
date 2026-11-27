-- RF-25, RN-23: a cópia que Cenários guarda de cada sinalização, recebida pelos eventos da Auditoria
-- (SinalizacaoRegistrada e SinalizacaoTriada). Com ela, o módulo decide sozinho se o cenário fica suspenso,
-- sem ler a tabela sinalizacao, que é da Auditoria. Só ids e códigos: a pergunta, a resposta e a
-- justificativa continuam só na Auditoria (RNF-01).
--
-- A suspensão não é gravada como causa: ela é refeita a partir das sinalizações da versão vigente. Quando
-- uma versão nova é aprovada, as sinalizações da anterior deixam de contar sem precisar mudar nada aqui.
CREATE TABLE sinalizacao_do_cenario (
    -- O id da sinalização na Auditoria. Sem FK para sinalizacao: Cenários não depende da Auditoria. A chave
    -- primária impede que um evento entregue de novo conte a mesma sinalização duas vezes.
    sinalizacao_id      BIGINT      PRIMARY KEY,
    -- Vem da própria versão na gravação, para não discordar dela.
    cenario_id          BIGINT      NOT NULL REFERENCES cenario (id),
    cenario_versao_id   BIGINT      NOT NULL REFERENCES cenario_versao (id),
    -- Só a sinalização grave do professor suspende o cenário na hora (RN-23a).
    perfil              TEXT        NOT NULL CHECK (perfil IN ('PROFESSOR', 'CURADOR')),
    gravidade_percebida TEXT        NOT NULL CHECK (gravidade_percebida IN ('LEVE', 'MODERADA', 'GRAVE')),
    -- Nula enquanto a sinalização está pendente (RF-24).
    decisao             TEXT        CHECK (decisao IN ('CONFIRMADA', 'DESCARTADA')),
    gravidade_final     TEXT        CHECK (gravidade_final IN ('LEVE', 'MODERADA', 'GRAVE')),
    triada_por_id       BIGINT      REFERENCES usuario (id),
    -- Quando a sinalização chegou a Cenários. Se a triagem chegar antes do registro, é o momento da triagem.
    recebida_em         TIMESTAMPTZ NOT NULL,
    triada_em           TIMESTAMPTZ,
    -- Como na sinalizacao: a pendente não tem triagem; a triada tem quem e quando; só a confirmada tem
    -- gravidade final.
    CONSTRAINT ck_sinalizacao_do_cenario_triagem CHECK (
        (decisao IS NULL AND gravidade_final IS NULL AND triada_por_id IS NULL AND triada_em IS NULL)
        OR (decisao = 'CONFIRMADA' AND gravidade_final IS NOT NULL AND triada_por_id IS NOT NULL
            AND triada_em IS NOT NULL)
        OR (decisao = 'DESCARTADA' AND gravidade_final IS NULL AND triada_por_id IS NOT NULL
            AND triada_em IS NOT NULL))
);

-- As regras de gravidade leem as sinalizações de uma versão (RN-23).
CREATE INDEX ix_sinalizacao_do_cenario_versao ON sinalizacao_do_cenario (cenario_versao_id);
