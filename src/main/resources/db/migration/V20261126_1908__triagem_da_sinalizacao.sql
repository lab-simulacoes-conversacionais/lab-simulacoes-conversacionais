-- RF-24, RN-22: o curador confirma ou descarta cada sinalização pendente. A confirmada leva a gravidade final
-- (Leve, Moderada ou Grave); a descartada, não. Quem triou e quando ficam na própria sinalização, além do log
-- (RN-35). Uma sinalização só é triada uma vez.
ALTER TABLE sinalizacao
    ADD COLUMN gravidade_final TEXT        CHECK (gravidade_final IN ('LEVE', 'MODERADA', 'GRAVE')),
    ADD COLUMN triada_por_id   BIGINT      REFERENCES usuario (id),
    ADD COLUMN triada_em       TIMESTAMPTZ,
    ADD CONSTRAINT ck_sinalizacao_status CHECK (status IN ('PENDENTE', 'CONFIRMADA', 'DESCARTADA')),
    -- A pendente não tem triagem; a triada tem quem e quando; só a confirmada tem gravidade final.
    ADD CONSTRAINT ck_sinalizacao_triagem CHECK (
        (status = 'PENDENTE' AND gravidade_final IS NULL AND triada_por_id IS NULL AND triada_em IS NULL)
        OR (status = 'CONFIRMADA' AND gravidade_final IS NOT NULL AND triada_por_id IS NOT NULL
            AND triada_em IS NOT NULL)
        OR (status = 'DESCARTADA' AND gravidade_final IS NULL AND triada_por_id IS NOT NULL
            AND triada_em IS NOT NULL));
