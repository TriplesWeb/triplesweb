import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("preview_deploy", Path(__file__).resolve().parents[1] / "deploy-pr-preview.py")
preview = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preview)

class PreviewIsolationTest(unittest.TestCase):
    def test_private_tables_are_not_copied(self):
        self.assertEqual(preview.public_tables(["artist","schedule","user","account","objekt_transfer","flyway_schema_history"]), ["artist","schedule","flyway_schema_history"])

    def test_isolated_database_names(self):
        self.assertEqual(preview.names("triples",26), ("triples_pr26_preview","pr_preview_26"))
        for database,pr in [("triples;DROP",26),("triples",0),("triples",10000)]:
            with self.assertRaises(ValueError):
                preview.names(database,pr)

    def make_compose(self, **overrides):
        args = dict(directory=Path("/srv/previews/pr-26"),pr=26,image="ghcr.io/triplesweb/triplesweb:pr-26-"+"a"*40,revision="a"*40,bind_ip="100.89.171.59",network="triples",database_host="triples-mariadb-1",database="triples_pr26_preview",username="pr_preview_26",password="test$pass",admin_password="test$admin",snapshot_at="2026-10-28T00:00:00Z")
        args.update(overrides)
        return preview.compose_spec(**args)

    def test_preview_cannot_target_production_or_public_interface(self):
        for override in [{"database":"triples"},{"username":"root"},{"bind_ip":"0.0.0.0"},{"image":"ghcr.io/triplesweb/triplesweb:latest"},{"revision":"b"*40}]:
            with self.assertRaises(ValueError):
                self.make_compose(**override)

    def test_session_uploads_and_database_are_isolated(self):
        service=self.make_compose()["services"]["preview_26"]
        self.assertEqual(service["ports"],["100.89.171.59:18026:8080"])
        self.assertTrue(all("/previews/pr-26/" in path for path in service["volumes"]))
        env=service["environment"]
        self.assertIn("/triples_pr26_preview?",env["SPRING_DATASOURCE_URL"])
        self.assertEqual(env["SPRING_DATASOURCE_PASSWORD"],"test$$pass")
        self.assertEqual(env["SERVER_SERVLET_SESSION_COOKIE_NAME"],"PR26SESSION")
        self.assertEqual(env["OBJEKT_INGEST_ENABLED"],"false")
        self.assertFalse(any("COSMO_TOKEN" in key for key in env))

if __name__ == "__main__":
    unittest.main()
