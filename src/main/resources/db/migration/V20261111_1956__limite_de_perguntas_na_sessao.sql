-- RN-18: o limite de perguntas é lido dos parâmetros ao iniciar a sessão e guardado nela, para uma
-- alteração do administrador valer só para as próximas sessões e não mudar uma aula em andamento.
-- O padrão só preenche as sessões que já existem, com o mesmo valor padrão do parâmetro; a aplicação
-- sempre informa o limite ao inserir.
ALTER TABLE sessao
    ADD COLUMN limite_perguntas INTEGER NOT NULL DEFAULT 40 CHECK (limite_perguntas > 0);
