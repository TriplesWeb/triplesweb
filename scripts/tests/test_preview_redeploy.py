import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location("mobile_deployment",Path(__file__).resolve().parents[1]/"deploy-pr-preview-mobile.py")
mobile=importlib.util.module_from_spec(spec)
spec.loader.exec_module(mobile)

class MobileRedeployTest(unittest.TestCase):
    def test_unchanged_password_preserves_configuration_and_login_session(self):
        first=mobile.access_config(None,"test-password")
        second=mobile.access_config(first,"test-password")
        self.assertEqual(first,second)

    def test_password_change_revokes_existing_session(self):
        first=mobile.access_config(None,"test-password")
        second=mobile.access_config(first,"changed-password")
        self.assertNotEqual(first["session"],second["session"])
        self.assertNotEqual(first["passwordHash"],second["passwordHash"])
