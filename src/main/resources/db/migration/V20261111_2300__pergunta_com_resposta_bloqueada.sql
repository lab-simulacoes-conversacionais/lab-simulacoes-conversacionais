-- RN-20: a pergunta cuja resposta foi bloqueada deixa de esperar resposta. A marca fica na própria
-- mensagem, em vez de comparar o horário da pergunta com o da pausa.
ALTER TABLE mensagem ADD COLUMN resposta_bloqueada BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE mensagem ADD CONSTRAINT ck_mensagem_bloqueio_so_da_turma
    CHECK (autoria = 'TURMA' OR NOT resposta_bloqueada);
