import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from preview import Reconciler, RecoveryFailed, Supervisor, Superseded
from data import references, upload_path


class ReconcilerTests(unittest.TestCase):
    def run_case(self, heads, fail_build=False, fail_deploy=False):
        history, deployments, statuses = iter(heads), [], []
        def build(sha):
            if fail_build:
                raise ValueError('build failed')
            return 'release-' + sha
        def deploy(sha, release):
            if fail_deploy:
                raise ValueError('server did not start')
            deployments.append(sha)
        manager = Reconciler(lambda: next(history), build, deploy, lambda **s: statuses.append(s), current='old')
        manager.tick()
        return manager, deployments, statuses

    def test_promotes_only_tested_current_commit(self):
        manager, deployments, statuses = self.run_case(['new', 'new'])
        self.assertEqual(manager.current, 'new')
        self.assertEqual(deployments, ['new'])
        self.assertEqual(statuses[-1]['phase'], 'ready')

    def test_stale_build_is_never_deployed(self):
        manager, deployments, statuses = self.run_case(['new', 'newer'])
        self.assertEqual(manager.current, 'old')
        self.assertEqual(deployments, [])
        self.assertEqual(statuses[-1]['phase'], 'superseded')

    def test_build_and_start_failures_keep_current(self):
        for kwargs in ({'fail_build': True}, {'fail_deploy': True}):
            with self.subTest(**kwargs):
                manager, deployments, statuses = self.run_case(['new', 'new'], **kwargs)
                self.assertEqual(manager.current, 'old')
                self.assertEqual(deployments, [])
                self.assertEqual(statuses[-1]['phase'], 'failed')

    def test_failure_has_retry_backoff_but_new_heads_are_not_blocked(self):
        builds = []
        def build(sha):
            builds.append(sha)
            raise ValueError()
        manager = Reconciler(lambda: 'new', build, lambda *_: None, lambda **_: None, clock=lambda: 0)
        manager.tick(); manager.tick()
        self.assertEqual(builds, ['new'])
        manager.remote = lambda: 'newer'
        manager.tick()
        self.assertEqual(builds, ['new', 'newer'])


class DeploymentTests(unittest.TestCase):
    def test_interrupted_deployment_recovers_before_starting_an_app(self):
        with tempfile.TemporaryDirectory() as directory:
            supervisor = Supervisor(directory)
            state = Path(directory)
            (state / 'deployment.json').write_text(json.dumps({'previous': {'commit': 'old', 'release': 'old'}, 'target': 'new'}))
            restored = []
            supervisor.restore_backup = lambda path: restored.append(path.name)
            supervisor.recover()
            self.assertEqual(restored, ['rollback'])
            self.assertEqual(supervisor.current['commit'], 'old')
            self.assertFalse((state / 'deployment.json').exists())

    def test_failed_start_restores_database_and_files_then_restarts_old_app(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory)
            supervisor = Supervisor(state, local_only=True)
            uploads = state / 'uploads'; uploads.mkdir()
            (uploads / 'file').write_text('before')
            supervisor.current = {'commit': 'old', 'release': str(state / 'old')}
            calls = []
            class Database:
                def backup(self, destination):
                    Path(destination).write_text('old database')
                def restore(self, dump):
                    calls.append(('restore', Path(dump).read_text()))
            supervisor.db = Database()
            def start(release):
                calls.append(('start', Path(release).name))
                if Path(release).name == 'new':
                    (uploads / 'file').write_text('after')
                    (uploads / 'extra').write_text('new')
                    raise ValueError('startup failed')
            supervisor.start_app = start
            supervisor.stop_app = lambda: calls.append(('stop', None))
            with patch('preview.control') as gateway:
                with self.assertRaises(ValueError):
                    supervisor.deploy('new', state / 'new')
                self.assertEqual(gateway.call_args.kwargs, {'port': 8888})
            self.assertEqual((uploads / 'file').read_text(), 'before')
            self.assertFalse((uploads / 'extra').exists())
            self.assertEqual(calls[-1], ('start', 'old'))
            self.assertIn(('restore', 'old database'), calls)
            self.assertEqual(supervisor.current['commit'], 'old')

    def test_drain_timeout_reopens_old_release_without_stopping_it(self):
        with tempfile.TemporaryDirectory() as directory:
            supervisor = Supervisor(directory)
            supervisor.current = {'commit': 'old', 'release': 'old'}
            stopped = []
            supervisor.stop_app = lambda: stopped.append(True)
            with patch('preview.control', side_effect=[RuntimeError('timeout'), {}]) as gateway:
                with self.assertRaises(RuntimeError):
                    supervisor.deploy('new', Path(directory) / 'new')
                self.assertEqual(gateway.call_args.args[1], 'activate')
            self.assertEqual(stopped, [])

    def test_file_references_reject_traversal_and_preserve_korean_paths(self):
        self.assertIsNone(upload_path('/uploads/../../secret'))
        self.assertIsNone(upload_path('/uploads/%2e%2e/secret'))
        self.assertEqual(upload_path('/uploads/images/%ED%95%9C.png'), 'images/한.png')
        self.assertEqual(references('<img src="/uploads/images/a.png">', 'content'), {'images/a.png'})


if __name__ == '__main__':
    unittest.main()
