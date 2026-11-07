# shellcheck shell=bash
# Troca da versão da aplicação, usada pelo deploy, pela reversão e pela restauração do banco.
# Carregue com source depois de lib/comum.sh e de chamar iniciar_ambiente.

PADRAO_TAG_ENTREGA='^v[0-9]+(\.[0-9]+)*$'
PADRAO_TAG_COMMIT='^sha-[0-9a-f]{7,40}$'

# Homologação aceita qualquer imagem publicada; o piloto sempre roda uma tag de entrega.
validar_tag() {
    local tag=$1
    if [[ "$tag" =~ $PADRAO_TAG_ENTREGA ]]; then
        return 0
    fi
    [[ "$AMBIENTE" == homologacao && "$tag" =~ $PADRAO_TAG_COMMIT ]] ||
        falhar "tag inválida para $AMBIENTE: '$tag'."
}

# Impede dois deploys simultâneos, inclusive um pelo GitHub e outro por SSH. A trava some com o script.
travar() {
    exec 9>"$DIR_AMBIENTE/.deploy.lock"
    flock --nonblock 9 || falhar "já existe um deploy ou uma reversão em andamento em $AMBIENTE."
}

# Cada linha: data, ação, tag de origem, tag de destino e resultado. Sem origem, usa "-".
registrar() {
    printf '%s acao=%s de=%s para=%s resultado=%s\n' \
        "$(date '+%FT%T%:z')" "$1" "${2:--}" "$3" "$4" >>"$DIR_AMBIENTE/historico-deploys.log"
}

gravar_tag() {
    sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$1/" "$ARQUIVO_ENV"
}

# Os || return são necessários: o set -e não vale dentro de uma função chamada em um if.
aplicar_tag() {
    local tag=$1 timeout
    timeout=$(ler_env DEPLOY_TIMEOUT)
    timeout=${timeout:-180}

    gravar_tag "$tag" || return 1
    log "Baixando a imagem $tag"
    compose pull app || return 1
    log "Subindo $tag e aguardando o health por até ${timeout}s"
    compose up -d --wait --wait-timeout "$timeout" app || return 1
}

# Troca para a tag pedida. Se ela não ficar saudável, volta sozinha para a tag que estava no ar.
# Saída: 0 trocou; 1 falhou, mas a tag anterior voltou; 2 falhou e a tag anterior também não subiu.
trocar_versao() {
    local acao=$1 nova=$2 atual
    atual=$(ler_env IMAGE_TAG)

    registrar "$acao" "$atual" "$nova" iniciado
    if aplicar_tag "$nova"; then
        registrar "$acao" "$atual" "$nova" ok
        log "$AMBIENTE está em $nova."
        return 0
    fi
    registrar "$acao" "$atual" "$nova" falhou

    if [[ -z "$atual" ]]; then
        erro "$nova não ficou saudável, e não há versão anterior para voltar."
        return 1
    fi

    erro "$nova não ficou saudável. Voltando para $atual."
    if aplicar_tag "$atual"; then
        registrar reversao-automatica "$nova" "$atual" ok
        erro "$AMBIENTE voltou para $atual. O deploy de $nova falhou."
        return 1
    fi
    registrar reversao-automatica "$nova" "$atual" falhou
    erro "A volta para $atual também falhou. Siga docs/operacao/reversao.md."
    return 2
}
