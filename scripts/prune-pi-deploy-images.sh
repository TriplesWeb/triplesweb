#!/usr/bin/env bash
# Source from a deployment script that holds the Pi deployment lock.
prune_deploy_images() {
  local previous_image="$1" repository=ghcr.io/triplesweb/triplesweb
  local history=.deploy-image-history container_ids protected_ids inventory
  local tag id reference temp failed=0
  local -a candidates=() retained=() old_refs=()
  local -A image_ids=() protected=() kept_ids=()

  inventory="$(docker image ls --no-trunc --format '{{.Tag}} {{.ID}}' "$repository")" || return 1
  while read -r tag id; do
    [[ "$tag" =~ ^(dev-)?[0-9a-f]{40}$ ]] || continue
    reference="$repository:$tag"
    candidates+=("$reference")
    image_ids["$reference"]="$id"
  done <<< "$inventory"
  [[ -n "${image_ids[$IMAGE]:-}" ]] || return 1

  container_ids="$(docker ps -aq)" || return 1
  [[ -n "$container_ids" ]] || return 1
  # Docker emits one container ID per line.
  local -a containers
  mapfile -t containers <<< "$container_ids"
  protected_ids="$(docker container inspect --format '{{.Image}}' "${containers[@]}")" || return 1
  while read -r id; do
    [[ -n "$id" ]] && protected["$id"]=1
  done <<< "$protected_ids"
  [[ -n "${protected[${image_ids[$IMAGE]}]:-}" ]] || return 1

  if [[ -e "$history" ]]; then
    mapfile -t old_refs < "$history" || return 1
  else
    # 첫 실행에는 직전 컨테이너 이미지 다음으로 최근 로컬 운영 이미지를 보관한다.
    old_refs=("${candidates[@]}")
  fi

  for reference in "$IMAGE" "$previous_image" "${old_refs[@]}"; do
    [[ "$reference" == "$repository:"* && "${reference#*:}" =~ ^[0-9a-f]{40}$ ]] || continue
    id="${image_ids[$reference]:-}"
    [[ -n "$id" && -z "${kept_ids[$id]:-}" ]] || continue
    retained+=("$reference")
    kept_ids["$id"]=1
    [[ "${#retained[@]}" -lt 4 ]] || break
  done

  temp="$(mktemp "$history.XXXXXX")" || return 1
  if ! printf '%s\n' "${retained[@]}" > "$temp" || ! mv -f "$temp" "$history"; then
    rm -f "$temp"
    return 1
  fi

  for reference in "${candidates[@]}"; do
    id="${image_ids[$reference]}"
    [[ -z "${protected[$id]:-}" && -z "${kept_ids[$id]:-}" ]] || continue
    docker image rm --no-prune "$reference" || failed=1
  done
  return "$failed"
}

cleanup_after_deploy() {
  if ! prune_deploy_images "$1"; then
    echo "Warning: TripleSWeb image cleanup did not complete" >&2
  fi
}

