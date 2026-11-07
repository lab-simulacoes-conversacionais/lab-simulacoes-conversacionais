#!/usr/bin/env bash
# Prepara uma VM Ubuntu 24.04 para os ambientes informados. Pode rodar de novo a qualquer momento: não
# sobrescreve os .env nem os backups. Rode a partir de um clone do repositório, com sudo.
#
# Uso: sudo infra/provision.sh --chave-deploy <arquivo.pub> <ambiente> [ambiente...]
# Exemplo, com os dois ambientes na mesma VM:
#   sudo infra/provision.sh --chave-deploy deploy.pub homologacao piloto
set -euo pipefail

RAIZ=/opt/poc23
USUARIO_DEPLOY=deploy
FUSO=America/Sao_Paulo
REPO=$(dirname "$(dirname "$(readlink -f "$0")")")

log() {
    printf '\n==> %s\n' "$*"
}

falhar() {
    printf 'ERRO: %s\n' "$*" >&2
    exit 1
}

conferir_requisitos() {
    [[ $EUID -eq 0 ]] || falhar "rode com sudo."

    # shellcheck disable=SC1091 # Arquivo do sistema.
    source /etc/os-release
    [[ "$ID" == ubuntu ]] || falhar "este script é para Ubuntu, e esta VM usa $ID."

    # O SSH vai recusar root e senha, e o usuário deploy não tem sudo. Sem outro administrador com
    # chave, ninguém mais conseguiria administrar a VM.
    local admin=${SUDO_USER:-}
    [[ -n "$admin" && "$admin" != root ]] ||
        falhar "entre com um usuário administrador (não root) e rode com sudo."
    id -nG "$admin" | grep -qw sudo || falhar "$admin precisa estar no grupo sudo."
    local chaves
    chaves="$(getent passwd "$admin" | cut -d: -f6)/.ssh/authorized_keys"
    [[ -s "$chaves" ]] || falhar "$admin precisa ter uma chave em $chaves antes de desativar o login por senha."
}

instalar_docker() {
    log "Docker Engine e plugin Compose"
    apt-get update -qq
    apt-get install -y -qq ca-certificates curl

    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc

    # shellcheck disable=SC1091 # Arquivo do sistema.
    source /etc/os-release
    cat >/etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $VERSION_CODENAME
Components: stable
Signed-By: /etc/apt/keyrings/docker.asc
EOF
    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    systemctl enable --now docker
}

configurar_logs_docker() {
    log "Rotação de logs do Docker"
    local novo
    novo=$(mktemp)
    cat >"$novo" <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "5"
  }
}
EOF
    # Reiniciar o Docker derruba os containers por instantes, então só reinicia se algo mudou.
    if cmp -s "$novo" /etc/docker/daemon.json; then
        rm -f "$novo"
    else
        install -m 0644 "$novo" /etc/docker/daemon.json
        rm -f "$novo"
        systemctl restart docker
    fi
}

criar_usuario_deploy() {
    local chave_publica=$1
    log "Usuário $USUARIO_DEPLOY"
    id "$USUARIO_DEPLOY" &>/dev/null || useradd --create-home --shell /bin/bash "$USUARIO_DEPLOY"
    usermod --lock "$USUARIO_DEPLOY"
    usermod --append --groups docker "$USUARIO_DEPLOY"

    local dir_ssh
    dir_ssh="$(getent passwd "$USUARIO_DEPLOY" | cut -d: -f6)/.ssh"
    install -d -m 700 -o "$USUARIO_DEPLOY" -g "$USUARIO_DEPLOY" "$dir_ssh"
    touch "$dir_ssh/authorized_keys"
    grep -qxF "$(cat "$chave_publica")" "$dir_ssh/authorized_keys" || cat "$chave_publica" >>"$dir_ssh/authorized_keys"
    chown "$USUARIO_DEPLOY:$USUARIO_DEPLOY" "$dir_ssh/authorized_keys"
    chmod 600 "$dir_ssh/authorized_keys"
}

endurecer_ssh() {
    log "SSH só com chave e sem root"
    # O prefixo 00 vem antes do 50-cloud-init.conf, e no sshd vale o primeiro valor lido.
    local conf=/etc/ssh/sshd_config.d/00-poc23.conf
    cat >"$conf" <<'EOF'
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
EOF
    mkdir -p /run/sshd
    if ! sshd -t; then
        rm -f "$conf"
        falhar "a configuração do SSH ficou inválida e foi desfeita."
    fi
    systemctl try-reload-or-restart ssh
}

configurar_firewall() {
    log "Firewall: só 22, 80 e 443"
    apt-get install -y -qq ufw
    ufw default deny incoming >/dev/null
    ufw default allow outgoing >/dev/null
    ufw allow 22/tcp >/dev/null
    ufw allow 80/tcp >/dev/null
    ufw allow 443/tcp >/dev/null
    ufw --force enable
}

ativar_atualizacoes_de_seguranca() {
    log "Atualizações de segurança automáticas"
    apt-get install -y -qq unattended-upgrades
    cat >/etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
}

copiar_se_ausente() {
    local origem=$1 destino=$2
    [[ -e "$destino" ]] || install -m 600 -o "$USUARIO_DEPLOY" -g "$USUARIO_DEPLOY" "$origem" "$destino"
}

preparar_ambiente() {
    local ambiente=$1
    local dir="$RAIZ/$ambiente"
    log "Ambiente $ambiente em $dir"

    install -d -m 750 -o "$USUARIO_DEPLOY" -g "$USUARIO_DEPLOY" "$dir" "$dir/infra"
    # Os backups têm as conversas das sessões.
    install -d -m 700 -o "$USUARIO_DEPLOY" -g "$USUARIO_DEPLOY" "$dir/backups"

    copiar_se_ausente "$REPO/infra/.env.example" "$dir/.env"
    sed -i "s/^AMBIENTE=$/AMBIENTE=$ambiente/" "$dir/.env"

    # O deploy atualiza estes arquivos depois, com a versão da tag implantada.
    cp "$REPO/infra/docker-compose.yml" "$dir/infra/"
    cp -r "$REPO/scripts" "$dir/"
    chown -R "$USUARIO_DEPLOY:$USUARIO_DEPLOY" "$dir/infra" "$dir/scripts"

    docker network inspect "poc23-borda-$ambiente" &>/dev/null || docker network create "poc23-borda-$ambiente" >/dev/null
    systemctl enable --now "poc23-backup@$ambiente.timer"
}

instalar_timers_de_backup() {
    log "Timers de backup"
    install -m 0644 "$REPO/infra/systemd/poc23-backup@.service" "$REPO/infra/systemd/poc23-backup@.timer" /etc/systemd/system/
    systemctl daemon-reload
}

preparar_borda() {
    local dir="$RAIZ/borda" nome
    log "Caddy em $dir"

    # O compose exige as duas redes, mesmo em uma VM que só tem um dos ambientes.
    for nome in homologacao piloto; do
        docker network inspect "poc23-borda-$nome" &>/dev/null || docker network create "poc23-borda-$nome" >/dev/null
    done

    install -d -m 750 -o "$USUARIO_DEPLOY" -g "$USUARIO_DEPLOY" "$dir"
    # A pasta é recriada para tirar o site de um ambiente que saiu desta VM.
    rm -rf "$dir/sites"
    install -d -m 750 -o "$USUARIO_DEPLOY" -g "$USUARIO_DEPLOY" "$dir/sites"
    for nome in "$@"; do
        install -m 640 -o "$USUARIO_DEPLOY" -g "$USUARIO_DEPLOY" "$REPO/infra/sites/$nome.caddy" "$dir/sites/"
    done
    # Com cp o arquivo mantém o inode, então o container segue enxergando o Caddyfile montado.
    cp "$REPO/infra/Caddyfile" "$REPO/infra/docker-compose.borda.yml" "$dir/"
    chown "$USUARIO_DEPLOY:$USUARIO_DEPLOY" "$dir/Caddyfile" "$dir/docker-compose.borda.yml"
    copiar_se_ausente "$REPO/infra/.env.borda.example" "$dir/.env"

    if ! grep -q '^ACME_EMAIL=.' "$dir/.env"; then
        printf '\nPreencha %s e rode este script de novo para subir o Caddy.\n' "$dir/.env"
        return 0
    fi
    docker compose --project-directory "$dir" --file "$dir/docker-compose.borda.yml" up -d --wait
    docker compose --project-directory "$dir" --file "$dir/docker-compose.borda.yml" \
        exec -T caddy caddy reload --config /etc/caddy/Caddyfile
}

main() {
    local chave_publica=
    if [[ "${1:-}" == --chave-deploy ]]; then
        chave_publica=${2:-}
        shift 2 || true
    fi
    [[ -r "$chave_publica" && $# -ge 1 ]] ||
        falhar "uso: sudo $0 --chave-deploy <arquivo.pub> <ambiente> [ambiente...]"

    local ambiente
    for ambiente in "$@"; do
        case "$ambiente" in
            homologacao | piloto) ;;
            *) falhar "ambiente inválido: '$ambiente'. Use homologacao ou piloto." ;;
        esac
    done

    conferir_requisitos
    export DEBIAN_FRONTEND=noninteractive
    timedatectl set-timezone "$FUSO"

    instalar_docker
    configurar_logs_docker
    criar_usuario_deploy "$chave_publica"
    endurecer_ssh
    configurar_firewall
    ativar_atualizacoes_de_seguranca

    install -d -m 755 "$RAIZ"
    instalar_timers_de_backup
    for ambiente in "$@"; do
        preparar_ambiente "$ambiente"
    done
    preparar_borda "$@"

    log "Pronto. Próximos passos em docs/operacao/hospedagem.md"
}

main "$@"
