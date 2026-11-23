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
printf '%s\n' "$PREVIOUS_TREE" > .deploy-migration-tree

docker() {
  echo "docker:$*" >> calls
  if [[ "$1 $2" == 'compose -f' ]]; then
    shift 3
    case "$*" in
      'config --quiet') ;;
      'ps -q mariadb') echo db-id ;;
      'ps -q app')
        if [[ -f candidate ]]; then echo new-id
        else echo old-id
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
      *Config.Image*) [[ "$4" == new-id ]] && echo "$IMAGE" || echo ghcr.io/triplesweb/triplesweb:old ;;
    esac
  elif [[ "$1 $2" == 'image inspect' ]]; then
    return 1
  else
    return 77
  fi
}

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
    def run_deploy(self, previous_tree="tree", failure="none"):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "functions.sh").write_text(FUNCTIONS)
            helper = DEPLOY.with_name("deploy-pi-image-retention.sh")
            (root / helper.name).write_text(helper.read_text())
            (root / "run.sh").write_text(SETUP)
            env = {**os.environ, "IMAGE": "ghcr.io/triplesweb/triplesweb:new", "APP_PORT": "8090",
                   "MIGRATIONS_TREE": "tree", "PREVIOUS_TREE": previous_tree,
                   "FAILURE": failure}
            result = subprocess.run(["bash", "run.sh"], cwd=root, env=env,
                                    capture_output=True, text=True, timeout=10)
            return result, (root / "calls").read_text(), (root / ".env").read_text()

    def assert_order(self, calls, *events):
        offsets = [calls.index(event) for event in events]
        self.assertEqual(offsets, sorted(offsets), calls)

    def test_single_app_deploy_stops_old_app_before_start(self):
        result, calls, settings = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assert_order(calls, "pull app", "stop -t 600 app",
                          "up -d --no-deps --force-recreate app", "probe")
        self.assertNotIn("docker:ps -aq", calls)
        self.assertEqual(settings, "IMAGE=ghcr.io/triplesweb/triplesweb:new\n")

    def test_pull_failure_keeps_the_existing_site_running(self):
        result, calls, _ = self.run_deploy(failure="pull")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(calls.count("pull app"), 5)
        self.assertNotIn("stop ", calls)

    def test_single_app_rollback_uses_the_previous_image_when_db_is_unchanged(self):
        result, calls, settings = self.run_deploy(failure="readiness")
        self.assertNotEqual(result.returncode, 0)
        self.assert_order(calls, "stop -t 600 app", "probe", "stop -t 10 app",
                          "restored:ghcr.io/triplesweb/triplesweb:old")
        self.assertIn("previous app is serving again", result.stderr)
        self.assertEqual(settings, "IMAGE=ghcr.io/triplesweb/triplesweb:old\n")

    def test_changed_migrations_run_after_stop_and_disable_automatic_image_rollback(self):
        result, calls, _ = self.run_deploy(previous_tree="older-tree", failure="readiness")
        self.assertNotEqual(result.returncode, 0)
        self.assert_order(calls, "pull app", "stop -t 600 app", "up -d --no-deps --force-recreate app",
                          "probe", "stop -t 10 app")
        self.assertNotIn("restored:", calls)
        self.assertIn("No healthy rollback", result.stderr)


if __name__ == "__main__":
    unittest.main()
