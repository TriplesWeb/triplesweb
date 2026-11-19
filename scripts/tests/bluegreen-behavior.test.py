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
switch_proxy() { echo "switch:$1:$2" >> calls; }
cleanup_after_deploy() { echo "cleanup:$1" >> calls; }
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
BASE_COMPOSE=(base_mock)
COMPOSE=(compose_mock)
'''


class DeploymentFailureTests(unittest.TestCase):
    def run_script(self, body):
        with tempfile.TemporaryDirectory(prefix="bluegreen-test-") as directory:
            root = Path(directory)
            (root / "functions.sh").write_text(FUNCTIONS)
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
                self.assertIn("stage:blue", calls)
                self.assertNotIn("cleanup:", calls)

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
run_deploy
''')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("stage:green", calls)
        self.assertIn("switch:blue:green", calls)
        self.assertIn("switch:blue:green\ncleanup:ghcr.io/triplesweb/triplesweb:old\n", calls)
        self.assertNotIn("stage:blue", calls)

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
