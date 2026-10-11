"""Source-level accessibility regression for MD3E freeform color action."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

BASE=Path(__file__).resolve().parents[2]
class PaletteA11y(unittest.TestCase):
    def test_palette_has_localized_description(self):
        code=(BASE/"src/com/android/customization/picker/color/ui/compose/ColorFloatingSheet.kt").read_text()
        block=code.split("onClick = navigateToFreeformPicker,",1)[1].split("Spacer(modifier = Modifier.height(8.dp))",1)[0]
        self.assertIn("stringResource(R.string.accessibility_custom_color_title)",block)
        self.assertNotIn('contentDescription = ""',block)
    def test_localized_string_exists_in_english_and_chinese(self):
        for name in ("values","values-zh-rCN"):
            root=ET.parse(BASE/"res"/name/"strings.xml").getroot()
            labels={e.attrib.get("name") for e in root}
            self.assertIn("accessibility_custom_color_title",labels)
if __name__=="__main__":unittest.main()
