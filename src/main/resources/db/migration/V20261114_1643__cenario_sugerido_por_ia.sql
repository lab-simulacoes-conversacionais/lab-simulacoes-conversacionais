-- RN-04: o cenário sugerido pela IA fica marcado "Sugerido por IA" para o curador conferir os fatos
-- com as fontes. Fica no cenário, e não na versão, porque continua valendo depois que o curador ajusta
-- o texto. Cenários criados do zero ficam sem a marca.
--
-- A marca tem coluna própria, e a referência aos metadados da geração (modelo e versão do prompt,
-- RNF-10) é só o rastro: se a retenção apagar a linha de resposta_ia_meta, a referência fica vazia e a
-- marca continua, como em caso_bloqueado.
ALTER TABLE cenario
    ADD COLUMN sugerido_por_ia     BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN sugestao_ia_meta_id BIGINT REFERENCES resposta_ia_meta (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_cenario_sugestao_ia CHECK (sugerido_por_ia OR sugestao_ia_meta_id IS NULL);
