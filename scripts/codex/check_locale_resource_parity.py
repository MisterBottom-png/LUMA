from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as element_tree
from dataclasses import dataclass
from pathlib import Path


SUPPORTED_LOCALES = ("et", "ru")
FORMAT_TOKEN = re.compile(
    r"%(?:(?P<position>\d+)\$)?(?P<flags>[-#+ 0,(<]*)?(?P<width>\d*)?(?P<precision>\.\d+)?(?P<conversion>[a-zA-Z])",
)


@dataclass(frozen=True)
class ResourceValue:
    key: str
    placeholders: tuple[str, ...]


def main(argv: list[str] | None = None) -> int:
    arguments = parse_arguments(argv)
    resource_root = arguments.resource_root.resolve()
    source_values = load_resources(resource_root / "values")
    errors: list[str] = []

    for locale in SUPPORTED_LOCALES:
        locale_values = load_resources(resource_root / f"values-{locale}")
        errors.extend(compare_resources(source_values, locale_values, locale))

    if errors:
        sys.stderr.write("\n".join(errors) + "\n")
        return 1
    return 0


def parse_arguments(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify supported Android locale resources match base resource keys and format placeholders.",
    )
    parser.add_argument(
        "--resource-root",
        type=Path,
        default=Path("app/src/main/res"),
        help="Android resource root containing values, values-et, and values-ru directories.",
    )
    return parser.parse_args(argv)


def load_resources(directory: Path) -> dict[str, ResourceValue]:
    if not directory.is_dir():
        return {}

    resources: dict[str, ResourceValue] = {}
    for path in sorted(directory.glob("*.xml")):
        root = element_tree.parse(path).getroot()
        for child in root:
            if child.attrib.get("translatable") == "false":
                continue
            if child.tag == "string":
                key = f"string:{child.attrib['name']}"
                resources[key] = ResourceValue(
                    key=key,
                    placeholders=placeholder_signature(text_content(child)),
                )
            elif child.tag == "plurals":
                for item in child.findall("item"):
                    quantity = item.attrib["quantity"]
                    key = f"plurals:{child.attrib['name']}:{quantity}"
                    resources[key] = ResourceValue(
                        key=key,
                        placeholders=placeholder_signature(text_content(item)),
                    )
    return resources


def compare_resources(
    source_values: dict[str, ResourceValue],
    locale_values: dict[str, ResourceValue],
    locale: str,
) -> list[str]:
    errors: list[str] = []
    for key, source_value in source_values.items():
        localized_value = locale_values.get(key)
        if localized_value is None:
            errors.append(f"{locale}: missing key {key}")
            continue
        if localized_value.placeholders != source_value.placeholders:
            errors.append(
                f"{locale}: placeholder mismatch for {key}: "
                f"expected {source_value.placeholders}, found {localized_value.placeholders}",
            )
    return errors


def text_content(element: element_tree.Element) -> str:
    return "".join(element.itertext())


def placeholder_signature(text: str) -> tuple[str, ...]:
    return tuple(
        "".join(
            part
            for part in (
                match.group("position"),
                match.group("conversion").lower(),
            )
            if part is not None
        )
        for match in FORMAT_TOKEN.finditer(text)
    )


if __name__ == "__main__":
    raise SystemExit(main())
