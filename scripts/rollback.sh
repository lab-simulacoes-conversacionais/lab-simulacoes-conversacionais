#!/usr/bin/env bash
# Volta o ambiente para uma versão anterior. Troca só a imagem: o banco fica como está.
# Uso: rollback.sh <ambiente> [tag]
#
# Sem tag, usa a versão que estava no ar antes do deploy da versão atual. Rodar de novo recua mais um
# passo no histórico (C, depois B, depois A) em vez de alternar entre as duas últimas.
set -euo pipefail

raiz_scripts=$(dirname "$(readlink -f "$0")")
# shellcheck source=lib/comum.sh
source "$raiz_scripts/lib/comum.sh"
# shellcheck source=lib/versao.sh
source "$raiz_scripts/lib/versao.sh"

[[ $# -ge 1 && $# -le 2 ]] || falhar "uso: $0 <ambiente> [tag]"
iniciar_ambiente "$1"
travar

versao_anterior() {
    local historico="$DIR_AMBIENTE/historico-deploys.log"
    [[ -r "$historico" ]] || return 0
    awk -v para="para=$1" '
        $2 == "acao=deploy" && $4 == para && $5 == "resultado=ok" { anterior = substr($3, 4) }
        END { if (anterior != "-") print anterior }
    ' "$historico"
}

tag=${2:-}
if [[ -z "$tag" ]]; then
    atual=$(ler_env IMAGE_TAG)
    tag=$(versao_anterior "$atual")
    [[ -n "$tag" ]] || falhar "não encontrei no histórico a versão anterior a '$atual'. Informe a tag."
fi
validar_tag "$tag"

log "Revertendo $AMBIENTE para $tag"
trocar_versao rollback "$tag"
