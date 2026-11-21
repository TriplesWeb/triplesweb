#!/usr/bin/env python3
"""Exercise deployment failure paths without Docker, SSH or a running server."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


DEPLOY = Path(__file__).resolve().parents[1] / "deploy-pi-bluegreen.sh"
FUNCTIONS = DEPLOY.read_text().split("# Keep flock and EXIT cleanup trap")[0]
SETUP = r'''
source ./functions.sh
mkdir -p bluegreen
printf 'IMAGE=ghcr.io/triplesweb/triplesweb:old\n' > .env
touch docker-compose.yml docker-compose.bluegreen.yml
is_running() { return 0; }
container_of() { echo "$1-id"; }
stage_candidate() { echo "stage:$1" >> calls; }
base_mock() {
  echo "base:$*" >> calls
  if [[ "$*" == 'ps -q app' ]]; then echo legacy-original-id; fi
}
compose_mock() {
  echo "compose:$*" >> calls
  if [[ "$*" == 'up -d --no-deps proxy' && "${FAIL_START:-false}" == true ]]; then return 1; fi
}
docker() {
  echo "docker:$*" >> calls
  if [[ "$1" == inspect ]]; then echo ghcr.io/triplesweb/triplesweb:old; fi
}
cleanup_after_deploy() { echo "cleanup:$1" >> calls; }
BASE_COMPOSE=(base_mock)
COMPOSE=(compose_mock)
'''


class DeploymentFailureTests(unittest.TestCase):
    def run_script(self, body):
        with tempfile.TemporaryDirectory(prefix="bluegreen-test-") as directory:
            root = Path(directory)
            (root / "functions.sh").write_text(FUNCTIONS)
            helper = DEPLOY.with_name("deploy-pi-image-retention.sh")
            (root / helper.name).write_text(helper.read_text())
            (root / "run.sh").write_text(SETUP + body)
            env = {**os.environ, "IMAGE": "ghcr.io/triplesweb/triplesweb:new",
                   "MIGRATIONS_TREE": "test-tree"}
            result = subprocess.run(["bash", "run.sh"], cwd=root, env=env,
                                    capture_output=True, text=True, timeout=10)
            calls = (root / "calls").read_text() if (root / "calls").exists() else ""
            return result, calls

    def test_bootstrap_restores_original_container_on_proxy_failure(self):
        for failure in ("start", "health"):
            with self.subTest(failure=failure):
                result, calls = self.run_script('''
wait_proxy_slot() { return 1; }
FAIL_START=%s
bootstrap_proxy
''' % ("true" if failure == "start" else "false"))
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("docker:start legacy-original-id", calls)
                self.assertNotIn("base:up", calls)
                self.assertNotIn("cleanup:", calls)
                self.assertIn("stage:blue", calls)

    def test_bootstrap_prunes_only_after_recording_success(self):
        result, calls = self.run_script(r'''
wait_proxy_slot() { return 0; }
remember_success() { echo "promoted:$1" >> calls; }
bootstrap_proxy
''')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls.splitlines()[-2:], [
            "promoted:blue", "cleanup:ghcr.io/triplesweb/triplesweb:old"
        ])

    def test_slot_switch_prunes_only_after_stopping_retired_slot(self):
        result, calls = self.run_script(r'''
write_proxy_config() { : > "$2"; }
remember_success() { echo "promoted:$1" >> calls; }
wait_proxy_slot() { return 0; }
proxy_points_to() { return 0; }
docker() { return 0; }
BG_DRAIN_SECONDS=0
touch "$NGINX_CONF"
switch_proxy blue green ghcr.io/triplesweb/triplesweb:old
''')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls.splitlines()[-3:], [
            "promoted:green", "compose:stop -t 600 app_blue",
            "cleanup:ghcr.io/triplesweb/triplesweb:old"
        ])

    def test_inconsistent_live_proxy_never_stages_or_stops_a_slot(self):
        for metadata in ("blue", "green"):
            with self.subTest(metadata=metadata):
                result, calls = self.run_script('''
printf '# ACTIVE_SLOT=blue\\n' > bluegreen/active.conf
printf '%s\\n' > bluegreen/active-slot
proxy_points_to() { [[ "$1" == green ]]; }
run_deploy
''' % metadata)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("both slots preserved", result.stderr)
                self.assertNotIn("stage:", calls)
                self.assertNotIn("stop", calls)

    def test_consistent_proxy_stages_only_inactive_slot(self):
        result, calls = self.run_script('''
printf '# ACTIVE_SLOT=blue\n' > bluegreen/active.conf
printf 'blue\n' > bluegreen/active-slot
proxy_points_to() { [[ "$1" == blue ]]; }
switch_proxy() { echo "switch:$1:$2" >> calls; }
run_deploy
''')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("stage:green", calls)
        self.assertIn("switch:blue:green", calls)
        self.assertNotIn("stage:blue", calls)

    def test_slow_readiness_probe_cannot_extend_warmup_deadline(self):
        result, calls = self.run_script(r'''
docker() { [[ "$1" != inspect ]] || echo 172.18.0.3; }
curl() {
  echo "curl:$*" >> calls
  SECONDS=$((SECONDS + 2))
}
BG_READY_TIMEOUT_SECONDS=2
wait_candidate blue
''')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Candidate timed out after 2 seconds", result.stderr)
        probes = [line for line in calls.splitlines() if line.startswith("curl:")]
        self.assertEqual(len(probes), 1)
        self.assertIn("--max-time 2", probes[0])
        self.assertIn("/actuator/health/readiness", probes[0])

    def test_warmup_requires_complete_home_page_response(self):
        for status, curl_exit, expected_ok in ((200, 0, True), (302, 0, False), (200, 28, False)):
            with self.subTest(status=status, curl_exit=curl_exit):
                result, calls = self.run_script(r'''
docker() {
  if [[ "$1" == inspect ]]; then echo 172.18.0.3; fi
}
curl() {
  echo "${@: -1}" >> calls
  case "${@: -1}" in
    */actuator/health/readiness) return 0 ;;
    */) echo %d; return %d ;;
    *) return 1 ;;
  esac
}
sleep() { :; }
BG_READY_TIMEOUT_SECONDS=2
wait_candidate blue
''' % (status, curl_exit))
                self.assertEqual(result.returncode == 0, expected_ok, result.stderr)
                self.assertIn("http://172.18.0.3:8080/\n", calls)

    def test_unsupported_memory_cgroup_aborts_before_container_changes(self):
        result, calls = self.run_script('''
docker() { echo "docker:$*" >> calls; echo false; }
check_memory
''')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Docker memory limits unsupported", result.stderr)
        self.assertNotIn("stop", calls)
        self.assertNotIn("up -d", calls)


if __name__ == "__main__":
    unittest.main()
