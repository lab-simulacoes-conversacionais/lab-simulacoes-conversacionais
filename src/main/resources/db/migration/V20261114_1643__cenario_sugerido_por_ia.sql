-- RN-04: o cenário sugerido pela IA fica marcado "Sugerido por IA" para o curador conferir os fatos
-- com as fontes. A marca é a própria referência aos metadados da geração, que guardam o modelo e a
-- versão do prompt (RNF-10). Fica no cenário, e não na versão, porque continua valendo depois que o
-- curador ajusta o texto. Cenários criados do zero ficam com a coluna vazia.
ALTER TABLE cenario
    ADD COLUMN sugestao_ia_meta_id BIGINT REFERENCES resposta_ia_meta (id);
