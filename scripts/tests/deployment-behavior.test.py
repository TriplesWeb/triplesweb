#!/usr/bin/env python3
"""Check the deployment order and failure recovery without a running Docker daemon."""

import os
from pathlib import Path
import subprocess
import tempfile
from textwrap import dedent
import unittest


DEPLOY = Path(__file__).resolve().parents[1] / "deploy-pi-app.sh"
FUNCTIONS = DEPLOY.read_text().split('\nexec 9>"$LOCK"\n')[0]
WORKFLOW = DEPLOY.parents[1] / ".github/workflows/deploy-main.yml"
PREFLIGHT = dedent(WORKFLOW.read_text().split('DECISION="$(ssh pi env ', 1)[1]
                   .split("bash -s <<'EOF'\n", 1)[1].split("          EOF\n", 1)[0])

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
        if [[ -f restored ]]; then echo restored-id
        elif [[ -f candidate ]]; then echo new-id
        else echo old-id
        fi ;;
      'pull app') [[ "$FAILURE" != pull ]] ;;
      'up -d --no-deps --force-recreate app') touch candidate ;;
      'up -d --no-deps app') echo "restored:$IMAGE" >> calls; touch restored ;;
      'stop -t 600 app'|'stop -t 10 app'|'logs --tail=120 app') ;;
      *) return 77 ;;
    esac
  elif [[ "$1" == inspect ]]; then
    case "$3" in
      *Health.Status*) echo healthy ;;
      *Config.Image*) [[ "$4" == new-id ]] && echo "$IMAGE" || echo "$RUNNING_IMAGE" ;;
      *Config.Env*)
        if [[ "$4" == restored-id ]]; then
          printf '%s\n' "OBJEKT_MINT_CHAIN_ENABLED=$RESTORED_CHAIN_ENABLED" "FLYWAY_ENABLED=$RESTORED_FLYWAY_ENABLED"
        elif [[ "$4" == new-id ]]; then
          printf '%s\n' 'OBJEKT_MINT_CHAIN_ENABLED=true' 'FLYWAY_ENABLED=true'
        else
          printf '%s\n' "OBJEKT_MINT_CHAIN_ENABLED=$CHAIN_ENABLED" "FLYWAY_ENABLED=$FLYWAY_ENABLED"
        fi ;;
    esac
  elif [[ "$1 $2" == 'image inspect' ]]; then
    return 1
  else
    return 77
  fi
}

sleep() { SECONDS=$((SECONDS + $1)); }
curl() {
  case "${*: -1}" in
    */actuator/health/readiness)
      echo 'probe' >> calls
      [[ "$FAILURE" != readiness || ! -f candidate || -f restored ]] ;;
    */cosmo/objekt/pulse)
      echo 'pulse' >> calls
      if [[ -f candidate && ! -f restored ]]; then
        case "$FAILURE" in
          chain) printf '%s' '{"chainMints":false}'; return ;;
          chain_string) printf '%s' '{"chainMints":"true"}'; return ;;
        esac
      fi
      printf '%s' '{"chainMints":true}' ;;
    */) printf '%s' '200' ;;
  esac
}
cleanup_after_deploy() { echo "cleanup:$1" >> calls; }
run_deploy
'''

PREFLIGHT_SETUP = r'''
docker() {
  if [[ "$1" == compose ]]; then
    echo app-id
  elif [[ "$1 $2" == 'inspect -f' ]]; then
    case "$3" in
      *Config.Image*) echo "$WANT_IMAGE" ;;
      *Config.Env*)
        [[ "$3" == *OBJEKT_MINT_CHAIN_ENABLED* && "$3" == *FLYWAY_ENABLED* ]] || return 91
        [[ -z "$CHAIN_ENABLED" ]] || echo "OBJEKT_MINT_CHAIN_ENABLED=$CHAIN_ENABLED"
        [[ -z "$FLYWAY_ENABLED" ]] || echo "FLYWAY_ENABLED=$FLYWAY_ENABLED" ;;
      *) return 92 ;;
    esac
  else
    return 93
  fi
}
curl() {
  if [[ "${*: -1}" == */cosmo/objekt/pulse ]]; then
    printf '{"chainMints":%s}' "$PULSE"
  else
    printf '%s' 200
  fi
}
'''


class DeploymentBehaviorTests(unittest.TestCase):
    def run_deploy(self, previous_tree="tree", failure="none", same_image=False,
                   chain_enabled="true", flyway_enabled="true", restored_chain="true", restored_flyway="true"):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "functions.sh").write_text(FUNCTIONS)
            helper = DEPLOY.with_name("deploy-pi-image-retention.sh")
            (root / helper.name).write_text(helper.read_text())
            (root / "run.sh").write_text(SETUP)
            env = {**os.environ, "IMAGE": "ghcr.io/triplesweb/triplesweb:new", "APP_PORT": "8090",
                   "MIGRATIONS_TREE": "tree", "PREVIOUS_TREE": previous_tree,
                   "FAILURE": failure, "APP_READY_TIMEOUT_SECONDS": "1",
                   "RUNNING_IMAGE": "ghcr.io/triplesweb/triplesweb:" + ("new" if same_image else "old"),
                   "CHAIN_ENABLED": chain_enabled, "FLYWAY_ENABLED": flyway_enabled,
                   "RESTORED_CHAIN_ENABLED": restored_chain, "RESTORED_FLYWAY_ENABLED": restored_flyway}
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
                          "up -d --no-deps --force-recreate app", "probe", "pulse")
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

    def test_workflow_skips_only_when_flags_and_pulse_are_true(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".dev-retired").touch()
            (root / ".env").write_text("APP_PORT=8090\n")
            (root / ".deploy-migration-tree").write_text("tree\n")
            for chain, flyway, pulse, expected in [
                ("true", "true", "true", "skip=true"), ("false", "true", "true", "skip=false"),
                ("true", "false", "true", "skip=false"), ("", "true", "true", "skip=false"),
                ("true", "", "true", "skip=false"), ("true", "true", "false", "skip=false"),
                ("true", "true", '"true"', "skip=false"),
            ]:
                with self.subTest(chain=chain, flyway=flyway, pulse=pulse):
                    env = {**os.environ, "PI_DEPLOY_PATH": str(root), "WANT_IMAGE": "image:sha",
                           "MIGRATIONS_TREE": "tree", "CHAIN_ENABLED": chain, "FLYWAY_ENABLED": flyway, "PULSE": pulse}
                    result = subprocess.run(["bash", "-s"], input=PREFLIGHT_SETUP + PREFLIGHT, env=env,
                                            capture_output=True, text=True, timeout=10)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertEqual(result.stdout.strip(), expected)

    def test_rollback_requires_both_flags_even_if_the_restored_pulse_would_be_true(self):
        for chain, flyway in [("false", "true"), ("true", "false")]:
            with self.subTest(chain=chain, flyway=flyway):
                result, calls, _ = self.run_deploy(failure="readiness", restored_chain=chain, restored_flyway=flyway)
                self.assertNotEqual(result.returncode, 0)
                self.assertRegex(calls, r'(?m)^docker:inspect .*Config.Env.* restored-id$')
                self.assertNotIn("pulse", calls)
                self.assertIn("No healthy rollback", result.stderr)

    def test_locked_deploy_recreates_same_image_when_a_running_flag_is_false(self):
        for chain, flyway in [("false", "true"), ("true", "false")]:
            with self.subTest(chain=chain, flyway=flyway):
                result, calls, _ = self.run_deploy(same_image=True, chain_enabled=chain, flyway_enabled=flyway)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assert_order(calls, "stop -t 600 app", "up -d --no-deps --force-recreate app", "pulse")

    def test_locked_deploy_skips_a_verified_chain_enabled_container(self):
        result, calls, _ = self.run_deploy(same_image=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("pulse", calls)
        self.assertNotIn("--force-recreate", calls)

    def test_successful_readiness_is_insufficient_without_a_boolean_chain_pulse(self):
        for failure in ["chain", "chain_string"]:
            with self.subTest(failure=failure):
                result, calls, settings = self.run_deploy(failure=failure)
                self.assertNotEqual(result.returncode, 0)
                self.assert_order(calls, "probe", "pulse", "stop -t 10 app",
                                  "restored:ghcr.io/triplesweb/triplesweb:old")
                self.assertEqual(settings, "IMAGE=ghcr.io/triplesweb/triplesweb:old\n")


if __name__ == "__main__":
    unittest.main()
