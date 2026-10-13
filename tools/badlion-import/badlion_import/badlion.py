"""Badlion's cosmetic definitions and where their files are expected.

Everything here follows the layout described in LAYOUT.md. `inspect` checks each rule against the real files
and reports what differs, so a wrong assumption shows up there instead of as a silent bad conversion.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import Any

from .archive import Archive, LayoutError

DEFINITIONS = "blc/cosmetics/"
COSMETICS_JSON = DEFINITIONS + "cosmetics.json"
MODELS_JSON = DEFINITIONS + "models.json"
EXAMPLE_JSON = DEFINITIONS + "example.json"
TEXTURE_ROOT = "assets/minecraft/blc/textures/cosmetics/"

# Texture folder per cosmetic type where it isn't simply the lowercase type.
FOLDERS = {"CAPE": "cloak", "WINGS": "wing"}

# Fields a cosmetic entry is expected to have, and its optional groups.
COSMETIC_FIELDS = (
    "cosmeticId", "name", "modelName", "hasModel", "allowChroma", "allowSaturation", "multiLayer",
    "multiLayerColoredIndex", "glow", "transparent", "particleSpawnRate", "renderPlayerHead", "defaultColor",
)
COSMETIC_GROUPS = ("animationData", "sizingData", "wingData", "petData", "cloakParticleData", "itemData")
MODEL_FIELDS = ("scale", "translation", "flipUVs", "applyChroma", "md5format", "subModelTexture", "useSubFolder")


def folder_for(cosmetic_type: str) -> str:
    return FOLDERS.get(cosmetic_type, cosmetic_type.lower())


@dataclass
class Cosmetic:
    type: str
    id: Any
    raw: dict

    @property
    def name(self) -> str:
        name = self.raw.get("name")
        return name if isinstance(name, str) else f"{self.type} {self.id}"

    @property
    def model_name(self) -> str | None:
        model = self.raw.get("modelName")
        return model.strip() or None if isinstance(model, str) else None

    @property
    def animated(self) -> bool:
        return isinstance(self.raw.get("animationData"), dict)

    @property
    def folder(self) -> str:
        return folder_for(self.type)

    @property
    def key(self) -> str:
        """Unique across types (cosmeticId is only unique within one)."""
        return f"{self.type}:{self.id}"

    def texture_path(self) -> str:
        f = self.folder
        if self.animated:
            return f"{TEXTURE_ROOT}{f}/{self.id}/{f}_{self.id}.avif"
        return f"{TEXTURE_ROOT}{f}/{f}_{self.id}.avif"


@dataclass
class Model:
    type: str
    name: str
    raw: dict
    users: list[Cosmetic] = field(default_factory=list)

    @property
    def md5(self) -> bool:
        return self.raw.get("md5format") is True

    @property
    def folder(self) -> str:
        return folder_for(self.type)

    def directory(self) -> str:
        return f"{TEXTURE_ROOT}{self.folder}/models/"

    def mesh_path(self) -> str:
        if self.md5:
            return f"{self.directory()}{self.name}/{self.name}.md5mesh"
        return f"{self.directory()}{self.name}.obj"

    def animation_paths(self, present: set[str]) -> list[str]:
        """The .md5anim files next to the .md5mesh."""
        if not self.md5:
            return []
        prefix = f"{self.directory()}{self.name}/"
        return sorted(p for p in present if p.startswith(prefix) and p.endswith(".md5anim") and "/" not in p[len(prefix):])


@dataclass
class Definitions:
    cosmetics: list[Cosmetic]
    models: dict[tuple[str, str], Model]
    cosmetics_chunk: int | None
    models_chunk: int | None
    example_text: str | None

    def by_type(self) -> dict[str, list[Cosmetic]]:
        out: dict[str, list[Cosmetic]] = {}
        for c in self.cosmetics:
            out.setdefault(c.type, []).append(c)
        return out

    def model_of(self, cosmetic: Cosmetic) -> Model | None:
        name = cosmetic.model_name
        return self.models.get((cosmetic.type, name)) if name else None


def _load_json(archive: Archive, path: str) -> Any:
    try:
        return json.loads(archive.read(path).decode("utf-8-sig"))
    except KeyError:
        raise LayoutError(f"{path} is not in any data jar") from None
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        raise LayoutError(f"{path} is not valid JSON: {e}") from e


def load(archive: Archive) -> Definitions:
    cosmetics_json = _load_json(archive, COSMETICS_JSON)
    registered = cosmetics_json.get("registeredCosmetics") if isinstance(cosmetics_json, dict) else None
    if not isinstance(registered, dict):
        found = sorted(cosmetics_json) if isinstance(cosmetics_json, dict) else type(cosmetics_json).__name__
        raise LayoutError(f"{COSMETICS_JSON}: expected 'registeredCosmetics' to map TYPE -> list, found {found}")
    cosmetics = []
    for cosmetic_type, entries in registered.items():
        if not isinstance(entries, list):
            raise LayoutError(f"{COSMETICS_JSON}: registeredCosmetics.{cosmetic_type} is a "
                              f"{type(entries).__name__}, expected a list")
        for i, entry in enumerate(entries):
            if not isinstance(entry, dict) or "cosmeticId" not in entry:
                raise LayoutError(f"{COSMETICS_JSON}: registeredCosmetics.{cosmetic_type}[{i}] has no cosmeticId")
            cosmetics.append(Cosmetic(cosmetic_type, entry["cosmeticId"], entry))

    models: dict[tuple[str, str], Model] = {}
    models_chunk = None
    if archive.has(MODELS_JSON):
        models_json = _load_json(archive, MODELS_JSON)
        data = models_json.get("modelData") if isinstance(models_json, dict) else None
        if not isinstance(data, dict):
            raise LayoutError(f"{MODELS_JSON}: expected 'modelData' to map TYPE -> name -> settings")
        for cosmetic_type, by_name in data.items():
            if not isinstance(by_name, dict):
                raise LayoutError(f"{MODELS_JSON}: modelData.{cosmetic_type} should map model names to settings")
            for name, settings in by_name.items():
                models[(cosmetic_type, name)] = Model(cosmetic_type, name, settings if isinstance(settings, dict) else {})
        models_chunk = archive.locate(MODELS_JSON)[0]
    definitions = Definitions(cosmetics, models, archive.locate(COSMETICS_JSON)[0], models_chunk,
                              archive.read(EXAMPLE_JSON).decode("utf-8-sig", "replace") if archive.has(EXAMPLE_JSON) else None)
    for c in cosmetics:
        model = definitions.model_of(c)
        if model is not None:
            model.users.append(c)
    return definitions


def strip_comments(text: str) -> str:
    """Removes // and /* */ comments outside strings (for example.json, a commented schema)."""
    out = []
    i = 0
    in_string = False
    while i < len(text):
        c = text[i]
        if in_string:
            out.append(c)
            if c == "\\" and i + 1 < len(text):
                out.append(text[i + 1])
                i += 1
            elif c == '"':
                in_string = False
        elif c == '"':
            in_string = True
            out.append(c)
        elif text.startswith("//", i):
            while i < len(text) and text[i] != "\n":
                i += 1
            continue
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = len(text) if end < 0 else end + 2
            continue
        else:
            out.append(c)
        i += 1
    # Trailing commas are common in hand-written examples.
    return re.sub(r",(\s*[}\]])", r"\1", "".join(out))


def slug(text: str) -> str:
    """Resource-location-safe slug: lowercase letters, digits and underscores."""
    s = re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")
    return s or "unnamed"


def cosmetic_id(cosmetic: Cosmetic) -> str:
    """blc_import:<type>_<id>_<slug>, e.g. blc_import:wings_6_angel."""
    return f"{slug(cosmetic.type)}_{slug(str(cosmetic.id))}_{slug(cosmetic.name)}"
