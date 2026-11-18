#!/usr/bin/env bash
set -Eeuo pipefail

# Run with Bash on a Linux host; Docker, Tailscale and the database are mocked.
test_root=$(mktemp -d)
trap 'rm -rf "$test_root"' EXIT
export TEST_ROOT="$test_root"
mkdir -p "$test_root/bin" "$test_root/production/uploads" "$test_root/production/data" "$test_root/state"
printf 'production image' > "$test_root/production/uploads/image.txt"
printf 'catalog' > "$test_root/production/data/videos.json"

cat > "$test_root/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -e
echo "$*" >> "$TEST_ROOT/docker.log"
case "$1 $2" in
  'compose ps') echo production-db ;;
  'info --format') echo "$TEST_ROOT" ;;
  'network create') touch "$TEST_ROOT/state/net-${*: -1}" ;;
  'network connect') test -f "$TEST_ROOT/state/$4" ;;
  'network inspect') test -f "$TEST_ROOT/state/net-$3" ;;
  'network rm') rm -f "$TEST_ROOT/state/net-$3" ;;
  'run -d')
    args=("$@"); for ((i=0; i<${#args[@]}; i++)); do
      if [[ ${args[i]} == --name ]]; then touch "$TEST_ROOT/state/${args[i+1]}"; fi
    done ;;
  'exec -i') cat >/dev/null ;;
  'exec '*)
    if [[ $* == *'du -sk'* ]]; then echo '1 /var/lib/mysql';
    elif [[ $* == *mariadb-dump* ]]; then echo 'database snapshot'; fi ;;
  'pull '*) : ;;
  'port '*) echo 127.0.0.1:31000 ;;
  'inspect -f') [[ ! -f "$TEST_ROOT/fail-health" ]] && echo true || echo false ;;
  'inspect '*) test -f "$TEST_ROOT/state/$2" ;;
  'stop '*) : ;;
  'start '*) : ;;
  'rm -fv') rm -f "$TEST_ROOT/state/$3" ;;
  *) echo "unexpected docker call: $*" >&2; exit 1 ;;
esac
EOF
cat > "$test_root/bin/tailscale" <<'EOF'
#!/usr/bin/env bash
if [[ $1 == status ]]; then
  echo '{"Self":{"DNSName":"pi.example.ts.net."}}'
elif [[ -f "$TEST_ROOT/serve-denied" && ${TEST_SERVE_ELEVATED:-} != yes ]]; then
  echo 'sending serve config: Access denied: serve config denied' >&2
  exit 1
else
  echo "$*" >> "$TEST_ROOT/serve.log"
fi
EOF
cat > "$test_root/bin/sudo" <<'EOF'
#!/usr/bin/env bash
[[ $1 == -n && $2 == -- ]] || exit 1
shift 2
TEST_SERVE_ELEVATED=yes "$@"
EOF
cat > "$test_root/bin/flock" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
cat > "$test_root/bin/ss" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
cat > "$test_root/bin/df" <<'EOF'
#!/usr/bin/env bash
if [[ -f "$TEST_ROOT/fail-space" ]]; then available=1; else available=10000000; fi
printf 'Filesystem 1024-blocks Used Available Capacity Mounted\nmock 10000000 0 %s 0%% /\n' "$available"
EOF
cat > "$test_root/bin/curl" <<'EOF'
#!/usr/bin/env bash
test ! -f "$TEST_ROOT/fail-health"
EOF
cat > "$test_root/bin/python3" <<'EOF'
#!/usr/bin/env bash
cat >/dev/null
echo pi.example.ts.net
EOF
chmod +x "$test_root/bin/"*
export PATH="$test_root/bin:$PATH"
script="$(cd "$(dirname "$0")/.." && pwd)/preview-pi.sh"
image="ghcr.io/triplesweb/triplesweb:pr-42-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
sha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
dir="$test_root/production/.previews/pr-42"

touch "$test_root/serve-denied"
bash "$script" deploy "$test_root/production" 42 "$image" "$sha" > "$test_root/result"
grep -q 'https://pi.example.ts.net:20000/' "$test_root/result"
rm "$test_root/serve-denied"
first=$(<"$dir/active")
test "$(cat "$dir/$first/uploads/image.txt")" = 'production image'
printf 'preview edit' > "$dir/$first/uploads/image.txt"

bash "$script" deploy "$test_root/production" 42 "$image" "$sha" > "$test_root/result"
second=$(<"$dir/active")
[[ $first != "$second" && ! -d $dir/$first ]]
test "$(cat "$dir/$second/uploads/image.txt")" = 'preview edit'
test "$(cat "$test_root/production/uploads/image.txt")" = 'production image'
grep -q "exec twpr42${first}-db sh -c" "$test_root/docker.log"
grep -q 'sh site$' "$test_root/docker.log"
grep -q 'sh full$' "$test_root/docker.log"
grep -q '127.0.0.1::8080 .* nginx:alpine' "$test_root/docker.log"
grep -q "exec twpr42${second}-db timeout -k 10s 12m" "$test_root/docker.log"
[[ ! -f $dir/$second/snapshot/db.sql.gz ]]

touch "$test_root/fail-space"
if bash "$script" deploy "$test_root/production" 42 "$image" "$sha" > /dev/null; then
  echo 'deployment unexpectedly proceeded without free space' >&2
  exit 1
fi
[[ $(<"$dir/active") == "$second" ]]
grep -q "start twpr42${second}-app" "$test_root/docker.log"
rm "$test_root/fail-space"

touch "$test_root/fail-health"
if bash "$script" deploy "$test_root/production" 42 "$image" "$sha" > /dev/null; then
  echo 'failed candidate unexpectedly deployed' >&2
  exit 1
fi
[[ $(<"$dir/active") == "$second" ]]
grep -q "start twpr42${second}-app" "$test_root/docker.log"
rm "$test_root/fail-health"

bash "$script" cleanup "$test_root/production" 42 > "$test_root/result"
[[ ! -d $dir ]]
test "$(cat "$test_root/production/uploads/image.txt")" = 'production image'
grep -q 'serve --https=20000 off' "$test_root/serve.log"
echo 'preview lifecycle test passed'
