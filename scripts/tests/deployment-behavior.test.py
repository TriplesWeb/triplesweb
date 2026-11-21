#!/usr/bin/env python3
"""Check the deployment order and failure recovery without a running Docker daemon."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


DEPLOY = Path(__file__).resolve().parents[1] / "deploy-pi-app.sh"
FUNCTIONS = DEPLOY.read_text().split('\nexec 9>"$LOCK"\n')[0]

SETUP = r'''
source ./functions.sh
printf 'IMAGE=ghcr.io/triplesweb/triplesweb:old\n' > .env
touch docker-compose.yml
mkdir -p bluegreen
if [[ "$LAYOUT" == legacy ]]; then
  printf '# ACTIVE_SLOT=blue\n' > bluegreen/active.conf
  printf '%s\n' "$PREVIOUS_TREE" > bluegreen/migration-tree
else
  printf '%s\n' "$PREVIOUS_TREE" > .deploy-migration-tree
fi

docker() {
  echo "docker:$*" >> calls
  if [[ "$1 $2" == 'compose -f' ]]; then
    shift 3
    case "$*" in
      'config --quiet') ;;
      'ps -q mariadb') echo db-id ;;
      'ps -q app')
        if [[ -f candidate ]]; then echo new-id
        elif [[ "$LAYOUT" == single ]]; then echo old-id
        fi ;;
      'pull app') [[ "$FAILURE" != pull ]] ;;
      'up -d --no-deps --force-recreate app') touch candidate ;;
      'up -d --no-deps app') echo "restored:$IMAGE" >> calls ;;
      'stop -t 600 app'|'stop -t 10 app'|'logs --tail=120 app') ;;
      *) return 77 ;;
    esac
  elif [[ "$1" == inspect ]]; then
    case "$3" in
      *Health.Status*) echo healthy ;;
      *com.docker.compose.project*) echo production ;;
      *State.Running*) [[ "$4" == green-id ]] && echo false || echo true ;;
      *Config.Image*) [[ "$4" == new-id ]] && echo "$IMAGE" || echo ghcr.io/triplesweb/triplesweb:old ;;
    esac
  elif [[ "$1" == ps ]]; then
    [[ "$*" == *label=com.docker.compose.project=production* ]] || return 77
    if [[ "$LAYOUT" == legacy ]]; then
      case "$*" in
        *service=proxy) echo proxy-id ;;
        *service=app_blue) echo blue-id ;;
        *service=app_green) echo green-id ;;
      esac
    fi
  elif [[ "$1 $2" == 'image inspect' ]]; then
    return 1
  else
    [[ "$1" == start || "$1" == stop || "$1" == rm ]] || return 77
  fi
}

curl() { printf 'x-bluegreen-slot: blue\r\n'; }
sleep() { :; }
wait_until_healthy() {
  echo 'probe' >> calls
  if [[ "$FAILURE" == readiness && ! -f failed_probe ]]; then
    touch failed_probe
    return 1
  fi
}
cleanup_after_deploy() { echo "cleanup:$1" >> calls; }
run_deploy
'''


class DeploymentBehaviorTests(unittest.TestCase):
    def run_deploy(self, layout="legacy", previous_tree="tree", failure="none"):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "functions.sh").write_text(FUNCTIONS)
            helper = DEPLOY.with_name("deploy-pi-image-retention.sh")
            (root / helper.name).write_text(helper.read_text())
            (root / "run.sh").write_text(SETUP)
            env = {**os.environ, "IMAGE": "ghcr.io/triplesweb/triplesweb:new", "APP_PORT": "8090",
                   "MIGRATIONS_TREE": "tree", "PREVIOUS_TREE": previous_tree,
                   "LAYOUT": layout, "FAILURE": failure}
            result = subprocess.run(["bash", "run.sh"], cwd=root, env=env,
                                    capture_output=True, text=True, timeout=10)
            return result, (root / "calls").read_text(), (root / ".env").read_text()

    def assert_order(self, calls, *events):
        offsets = [calls.index(event) for event in events]
        self.assertEqual(offsets, sorted(offsets), calls)

    def test_cutover_reuses_the_port_after_stopping_the_old_apps_and_proxy(self):
        result, calls, settings = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assert_order(calls, "pull app", "docker:stop -t 600 blue-id", "docker:stop -t 10 proxy-id",
                          "up -d --no-deps --force-recreate app", "probe", "docker:rm proxy-id blue-id green-id")
        self.assertEqual(settings, "IMAGE=ghcr.io/triplesweb/triplesweb:new\n")

    def test_pull_failure_keeps_the_existing_site_running(self):
        result, calls, _ = self.run_deploy(failure="pull")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(calls.count("pull app"), 5)
        self.assertNotIn("stop ", calls)

    def test_failed_cutover_restores_the_original_containers_when_db_is_unchanged(self):
        result, calls, settings = self.run_deploy(failure="readiness")
        self.assertNotEqual(result.returncode, 0)
        self.assert_order(calls, "up -d --no-deps --force-recreate app", "probe",
                          "stop -t 10 app", "docker:start blue-id", "docker:start proxy-id")
        self.assertIn("previous app is serving again", result.stderr)
        self.assertNotIn("docker:rm ", calls)
        self.assertEqual(settings, "IMAGE=ghcr.io/triplesweb/triplesweb:old\n")

    def test_single_app_rollback_uses_the_previous_image_when_db_is_unchanged(self):
        result, calls, settings = self.run_deploy(layout="single", failure="readiness")
        self.assertNotEqual(result.returncode, 0)
        self.assert_order(calls, "stop -t 600 app", "probe", "stop -t 10 app",
                          "restored:ghcr.io/triplesweb/triplesweb:old")
        self.assertIn("previous app is serving again", result.stderr)
        self.assertEqual(settings, "IMAGE=ghcr.io/triplesweb/triplesweb:old\n")

    def test_changed_migrations_run_after_stop_and_disable_automatic_image_rollback(self):
        result, calls, _ = self.run_deploy(layout="single", previous_tree="older-tree", failure="readiness")
        self.assertNotEqual(result.returncode, 0)
        self.assert_order(calls, "pull app", "stop -t 600 app", "up -d --no-deps --force-recreate app",
                          "probe", "stop -t 10 app")
        self.assertNotIn("restored:", calls)
        self.assertIn("No healthy rollback", result.stderr)


if __name__ == "__main__":
    unittest.main()
