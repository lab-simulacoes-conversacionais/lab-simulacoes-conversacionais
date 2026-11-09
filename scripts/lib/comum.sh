# shellcheck shell=bash
# Funções usadas por todos os scripts de operação. Carregue com source, não execute.

POC23_RAIZ="${POC23_RAIZ:-/opt/poc23}"

log() {
    printf '[%s] %s\n' "$(date '+%F %T')" "$*"
}

erro() {
    printf '[%s] ERRO: %s\n' "$(date '+%F %T')" "$*" >&2
}

falhar() {
    erro "$*"
    exit 1
}

# Define AMBIENTE, DIR_AMBIENTE e ARQUIVO_ENV. Chame antes de qualquer outra função daqui.
iniciar_ambiente() {
    case "${1:-}" in
        homologacao | piloto) ;;
        *) falhar "ambiente inválido: '${1:-}'. Use homologacao ou piloto." ;;
    esac

    AMBIENTE=$1
    DIR_AMBIENTE="$POC23_RAIZ/$AMBIENTE"
    ARQUIVO_ENV="$DIR_AMBIENTE/.env"
    [[ -r "$ARQUIVO_ENV" ]] || falhar "não consegui ler $ARQUIVO_ENV. Rode o provision.sh primeiro."
}

compose() {
    docker compose \
        --project-name "poc23-$AMBIENTE" \
        --project-directory "$DIR_AMBIENTE" \
        --env-file "$ARQUIVO_ENV" \
        --file "$DIR_AMBIENTE/infra/docker-compose.yml" \
        "$@"
}

# Lê uma chave do .env sem executá-lo, porque ele guarda segredos que podem ter qualquer caractere.
ler_env() {
    sed -n "s/^$1=//p" "$ARQUIVO_ENV" | tail -n 1
}
