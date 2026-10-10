from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
ANDROID_NS = "http://schemas.android.com/apk/res/android"


class PredictiveBackManifestTest(unittest.TestCase):
    def test_main_activity_inherits_predictive_back_opt_in(self) -> None:
        root = ET.parse(MANIFEST).getroot()
        application = root.find("application")

        self.assertIsNotNone(application)
        self.assertEqual(
            "true",
            application.get(f"{{{ANDROID_NS}}}enableOnBackInvokedCallback"),
        )


if __name__ == "__main__":
    unittest.main()
