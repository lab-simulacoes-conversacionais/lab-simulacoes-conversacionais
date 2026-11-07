# Roteiro de reversão

Como voltar um ambiente para a versão anterior em menos de 15 minutos (RNF-12). A reversão troca só a imagem da aplicação: o banco continua como está. Leia a seção [E o banco?](#e-o-banco) antes de reverter uma versão que trouxe migration.

## Quando reverter

Reverta assim que uma versão nova atrapalhar a aula e não houver correção em poucos minutos:
- tela com erro;
- login falhando;
- respostas da IA que não chegam;
- `/actuator/health` diferente de `{"status":"UP"}`.

Primeiro reverta e depois investigue com calma.

Você **não** precisa reverter quando o deploy falhou sozinho. Se a versão nova não fica saudável, o `deploy.sh` já volta para a anterior e o job do GitHub termina com erro. Confira em `historico-deploys.log` se aparece `acao=reversao-automatica ... resultado=ok`.

## 1. Reverter pelo GitHub

1. Em *Actions*, abra **Rollback** e clique em *Run workflow*.
2. Escolha o ambiente e deixe a **tag vazia** para voltar à versão que estava no ar antes da atual. Preencha a tag só para ir a uma versão específica, como `v0.1`.
3. **No piloto,** o job espera a aprovação de um dos revisores: abra o job e clique em *Review deployments*. Quem disparou também pode aprovar.
4. Se o job ficar parado em *Waiting* e outro *Deploy* do mesmo ambiente estiver aguardando aprovação, rejeite esse deploy. Os dois usam a mesma fila, e a reversão só começa depois que ele sair.

Se o GitHub estiver fora do ar ou ninguém puder aprovar, use o caminho por SSH.

## 2. Reverter por SSH

Precisa da chave de um administrador da VM ou da chave de deploy:

```
ssh deploy@IP_DA_VM
/opt/poc23/piloto/scripts/rollback.sh piloto          # volta para a versão anterior
/opt/poc23/piloto/scripts/rollback.sh piloto v0.1     # ou vai para uma tag específica
```

Rodar sem tag de novo recua mais um passo no histórico (v0.3, depois v0.2, depois v0.1).

**Possíveis mensagens:**
- *"já existe um deploy ou uma reversão em andamento"*: espere o outro terminar.
- *"não encontrei no histórico a versão anterior"*: informe a tag.

## 3. Confirmar que voltou

```
curl -s https://DOMINIO_PILOTO/actuator/health          # {"status":"UP"}
grep IMAGE_TAG /opt/poc23/piloto/.env                   # a tag de destino
tail -3 /opt/poc23/piloto/historico-deploys.log         # acao=rollback ... resultado=ok
```

Depois, entre no sistema pela tela de login e abra uma tela que estava com problema.

**Se a volta também falhar,** o script termina com o código 2 e a mensagem *"A volta para ... também falhou"*. Isso quase sempre indica um problema no banco: veja a próxima seção.

## E o banco?

A reversão troca só a imagem. **O Flyway não desfaz migrations:** as tabelas e colunas criadas pela versão nova continuam no banco depois de voltar para a anterior. Por isso a reversão simples só funciona quando a versão anterior ainda aceita o banco atual.

### As migrations precisam ser retrocompatíveis

- **Expandir antes, contrair depois.** Uma versão só adiciona o que precisa: tabela nova, coluna nova que aceita nulo ou tem valor padrão, índice. Remover ou renomear algo fica para uma versão posterior, quando a anterior já não for mais candidata a reversão.
- **O build garante a primeira parte:** o `MigrationsTest` falha se uma migration tiver `DROP`, `RENAME`, `ALTER COLUMN`, `DELETE FROM` ou `TRUNCATE`. Uma migration de contração exige mudar esse teste de propósito, com o grupo sabendo.
- **A versão antiga ignora migrations futuras.** Está configurado em `spring.flyway.ignore-migration-patterns=*:future`, e o `MigrationsTest` confere com o Flyway real. Também foi testado de ponta a ponta com as imagens:
  - a v0.0.2, com migration nova, foi para o ar;
  - a reversão voltou para a v0.0.1;
  - a v0.0.1 subiu com o aviso do Flyway *"Schema "public" has a version (20261108.0900) that is newer than the latest available migration"*;
  - os dados da tabela nova continuaram no banco.

### Se uma migration destrutiva foi ao ar

Neste caso a reversão simples falha, porque a versão anterior não aceita o banco sem a coluna ou a tabela que ela usa. O script volta sozinho para a versão nova e termina com erro. O caminho é **restaurar o backup feito antes do deploy**, voltando a aplicação para a versão anterior no mesmo comando:

```
ls -t /opt/poc23/piloto/backups/ | head            # o backup de antes do deploy
grep 'para=v0.3' /opt/poc23/piloto/historico-deploys.log | head -1   # horário do deploy
/opt/poc23/piloto/scripts/restore-postgres.sh piloto poc23-piloto-20261113-180501.dump v0.2
```

O deploy no piloto sempre gera um backup imediatamente antes. Escolha o mais recente com horário anterior ao do deploy no histórico.

**Atenção: tudo o que foi gravado entre o backup e a restauração se perde.** Na simulação local, a reversão que falhou levou 43 s e a restauração com a tag levou 29 s.

## Teste de reversão

O teste prova o RNF-12 e precisa ser repetido na VM real. Faça fora do horário de aula.

1. Garanta que o ambiente tem pelo menos dois deploys bem-sucedidos (A e depois B) e que B está no ar.
2. Anote a hora de início e dispare a reversão sem tag, pelo GitHub ou por SSH.
3. Pare o cronômetro quando `/actuator/health` responder `UP` pelo domínio e `IMAGE_TAG` mostrar A.
4. Volte para B com um deploy normal.
5. Registre o resultado na tabela abaixo, pelo PR que fechar o teste.

Pelo GitHub, o tempo inclui a fila do runner e a aprovação. Se passar de 15 minutos, registre de onde veio a demora.

| Data | Ambiente | Caminho | De | Para | Tempo | Responsável | Observações |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 07/11/2026 | Simulação local | `rollback.sh` | v0.0.2 | v0.0.1 | 26 s | Isadora | Docker na máquina de desenvolvimento, imagens já baixadas. A v0.0.2 tinha migration nova. |
| | VM `homologacao` | GitHub | | | | | |
| | VM `piloto` | GitHub | | | | | |
