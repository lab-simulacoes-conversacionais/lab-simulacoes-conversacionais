#!/usr/bin/env bash
# Coloca uma tag da imagem no ar e volta sozinho para a anterior se ela não ficar saudável.
# Uso: deploy.sh <ambiente> <tag>
set -euo pipefail

raiz_scripts=$(dirname "$(readlink -f "$0")")
# shellcheck source=lib/comum.sh
source "$raiz_scripts/lib/comum.sh"
# shellcheck source=lib/versao.sh
source "$raiz_scripts/lib/versao.sh"

[[ $# -eq 2 ]] || falhar "uso: $0 <ambiente> <tag>"
iniciar_ambiente "$1"
validar_tag "$2"
travar

trocar_versao deploy "$2"
