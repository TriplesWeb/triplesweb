#!/usr/bin/env bash
# Run ON the Raspberry Pi (after copying docker-compose.yml + .env here).
set -euo pipefail

DEPLOY_DIR="${1:-/opt/stacks/triplesweb}"

echo "==> Deploy dir: ${DEPLOY_DIR}"
mkdir -p "${DEPLOY_DIR}"
cd "${DEPLOY_DIR}"

mkdir -p uploads data

if [[ ! -f docker-compose.yml ]]; then
  echo "ERROR: docker-compose.yml missing in ${DEPLOY_DIR}"
  echo "Copy it from your Mac first, e.g.:"
  echo "  scp docker-compose.yml .env pi@<host>:/opt/stacks/triplesweb/"
  exit 1
fi

if [[ ! -f .env ]]; then
  echo "ERROR: .env missing in ${DEPLOY_DIR}"
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "==> Installing Docker"
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker "$(id -un)"
  echo "Docker installed. Log out/in (or reboot), then re-run this script."
  exit 0
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: docker compose plugin missing"
  exit 1
fi

SSH_KEY="${HOME}/.ssh/triplesweb_deploy"
if [[ ! -f "${SSH_KEY}" ]]; then
  echo "==> Creating deploy SSH key"
  mkdir -p "${HOME}/.ssh"
  chmod 700 "${HOME}/.ssh"
  ssh-keygen -t ed25519 -f "${SSH_KEY}" -N "" -C "triplesweb-deploy"
  cat "${SSH_KEY}.pub" >> "${HOME}/.ssh/authorized_keys"
  chmod 600 "${HOME}/.ssh/authorized_keys"
  echo
  echo "Add this PRIVATE key to GitHub Secret PI_SSH_KEY:"
  echo "-----"
  cat "${SSH_KEY}"
  echo "-----"
fi

echo
echo "==> Next (when GHCR image exists):"
echo "  cd ${DEPLOY_DIR}"
echo "  docker login ghcr.io   # if package is private"
echo "  docker compose pull && docker compose up -d"
echo
echo "GitHub Secrets reminder:"
echo "  PI_HOST         = this machine Tailscale name/IP"
echo "  PI_USER         = $(id -un)"
echo "  PI_DEPLOY_PATH  = ${DEPLOY_DIR}"
echo "  PI_SSH_KEY      = contents of ${SSH_KEY}"
echo
echo "Done (folder + dirs + deploy key ready)."
