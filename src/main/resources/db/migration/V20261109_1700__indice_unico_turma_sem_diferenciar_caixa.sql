-- RN-32: "8º A" e "8º a" são a mesma turma. O uq_turma diferencia maiúsculas e minúsculas, então
-- este índice acrescenta a mesma regra sem diferenciá-las. O uq_turma fica, porque só se acrescenta.
CREATE UNIQUE INDEX uq_turma_sem_caixa
    ON turma (lower(escola), lower(ano_serie), lower(turno), lower(rotulo));
