#!/usr/bin/env bash
# Gera um dump do PostgreSQL do ambiente, confere o arquivo e apaga os backups antigos.
# Uso: backup-postgres.sh <ambiente>
#
# Os backups contêm as conversas das sessões. Ver "Backups" em docs/operacao/hospedagem.md.
set -euo pipefail

# shellcheck source=lib/comum.sh
source "$(dirname "$(readlink -f "$0")")/lib/comum.sh"

[[ $# -eq 1 ]] || falhar "uso: $0 <ambiente>"
iniciar_ambiente "$1"

if [[ -z "$(ler_env IMAGE_TAG)" ]]; then
    log "$AMBIENTE ainda não teve deploy, então não há banco para copiar."
    exit 0
fi

retencao=$(ler_env BACKUP_RETENCAO_DIAS)
retencao=${retencao:-14}
[[ "$retencao" =~ ^[1-9][0-9]*$ ]] || falhar "BACKUP_RETENCAO_DIAS inválido: '$retencao'"

umask 077
dir_backups="$DIR_AMBIENTE/backups"
mkdir -p "$dir_backups"
arquivo="$dir_backups/poc23-$AMBIENTE-$(date +%Y%m%d-%H%M%S).dump"
parcial="$arquivo.parcial"
trap 'rm -f "$parcial"' EXIT

log "Gerando $arquivo"
# shellcheck disable=SC2016 # As variáveis são as do container do PostgreSQL.
compose exec -T postgres sh -c 'pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"' >"$parcial"

# Só depois de conferir o arquivo ganha o nome final, e só então os antigos podem ser apagados.
if ! itens=$(compose exec -T postgres pg_restore --list <"$parcial") || [[ -z "$itens" ]]; then
    falhar "o arquivo gerado não é um dump válido."
fi
mv "$parcial" "$arquivo"
log "Backup conferido: $(du -h "$arquivo" | cut -f1), $(grep -cv '^;' <<<"$itens") objetos."

find "$dir_backups" -maxdepth 1 -type f -name 'poc23-*.dump' -mmin "+$((retencao * 1440))" -print -delete |
    while read -r apagado; do log "Apagado por retenção: $apagado"; done
