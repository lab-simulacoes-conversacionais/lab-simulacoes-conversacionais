#!/usr/bin/env bash
# Substitui o banco do ambiente pelo conteúdo de um backup. Apaga tudo o que foi gravado depois dele.
# Uso: restore-postgres.sh <ambiente> <arquivo> [tag]
#
# Informe a tag quando o backup for anterior a uma migration destrutiva: a aplicação volta nessa
# versão, em vez de subir de novo a versão atual, que reaplicaria a migration.
set -euo pipefail

raiz_scripts=$(dirname "$(readlink -f "$0")")
# shellcheck source=lib/comum.sh
source "$raiz_scripts/lib/comum.sh"
# shellcheck source=lib/versao.sh
source "$raiz_scripts/lib/versao.sh"

[[ $# -ge 2 && $# -le 3 ]] || falhar "uso: $0 <ambiente> <arquivo> [tag]"
iniciar_ambiente "$1"
tag=${3:-}
[[ -z "$tag" ]] || validar_tag "$tag"

arquivo=$2
[[ -f "$arquivo" ]] || arquivo="$DIR_AMBIENTE/backups/$2"
[[ -r "$arquivo" ]] || falhar "backup não encontrado: $2"

travar

compose exec -T postgres pg_restore --list <"$arquivo" >/dev/null ||
    falhar "$arquivo não é um dump válido. Nada foi alterado."

echo "Isto vai APAGAR o banco de $AMBIENTE e colocar no lugar o conteúdo de:"
echo "  $arquivo"
read -r -p "Para confirmar, digite o nome do ambiente ($AMBIENTE): " confirmacao
[[ "$confirmacao" == "$AMBIENTE" ]] || falhar "confirmação diferente de '$AMBIENTE'. Nada foi alterado."

log "Guardando o estado atual antes de apagar"
"$raiz_scripts/backup-postgres.sh" "$AMBIENTE"

log "Parando a aplicação"
compose stop app

# Recria o banco em vez de usar pg_restore --clean, que só remove o que existe no dump: as tabelas de
# uma migration mais nova ficariam para trás, e o Flyway falharia ao aplicá-la de novo.
log "Recriando o banco"
# shellcheck disable=SC2016 # As variáveis são as do container do PostgreSQL.
compose exec -T postgres sh -c '
    dropdb --force --if-exists --maintenance-db=postgres -U "$POSTGRES_USER" "$POSTGRES_DB" &&
    createdb --maintenance-db=postgres -U "$POSTGRES_USER" "$POSTGRES_DB"
'

log "Restaurando $arquivo"
# shellcheck disable=SC2016 # As variáveis são as do container do PostgreSQL.
compose exec -T postgres sh -c \
    'pg_restore --exit-on-error --single-transaction --no-owner -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
    <"$arquivo" ||
    falhar "a restauração falhou e a aplicação segue parada. O estado anterior está no backup mais recente."

if [[ -n "$tag" ]]; then
    trocar_versao restauracao "$tag"
else
    log "Subindo a aplicação"
    compose up -d --wait --wait-timeout "$(ler_env DEPLOY_TIMEOUT)" app
fi
log "Banco de $AMBIENTE restaurado."
