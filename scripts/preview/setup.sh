#!/usr/bin/env bash
set -euo pipefail
preview_script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
preview_tools_dir="${PREVIEW_TOOLS_DIR:-/workspace/.onboarding/pr58-preview-tools}"
mkdir -p "$preview_tools_dir"
node -e 'if (Number(process.versions.node.split(".")[0]) < 20) process.exit(1)'
python3 -m venv "$preview_tools_dir/venv"
"$preview_tools_dir/venv/bin/python" -m pip install --cache-dir "$preview_tools_dir/pip-cache" -r "$preview_script_dir/requirements.txt"
npm --prefix "$preview_script_dir" ci --cache "$preview_tools_dir/npm-cache" --ignore-scripts --no-audit --no-fund
mkdir -p "$preview_tools_dir/bin"
printf '#!/usr/bin/env bash\nset -euo pipefail\nexec %q %q "$@"\n' \
  "$preview_tools_dir/venv/bin/python" "$preview_script_dir/preview.py" > "$preview_tools_dir/bin/preview"
chmod 700 "$preview_tools_dir/bin/preview"
printf '설치 완료: %s/bin/preview\n' "$preview_tools_dir"
