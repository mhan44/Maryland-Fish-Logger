
from pathlib import Path
import shutil
import yaml

PROJECT_DIR = Path(__file__).resolve().parent.parent
DATASET_DIR = PROJECT_DIR / "Fish Finder.v17i.yolo26"
TARGET = PROJECT_DIR / "DATA_MD_SUBSET"

# Initial 8-species model
SELECTED_SPECIES = [
    "Perch - White",
    "Bowfin",
    "Crappie - Black",
    "Bass - Smallmouth",
    "Catfish - Blue",
    "Catfish - Flathead",
    "Bluegill",
    "Shad - American",
    "Bass - Largemouth",
    "Bass - Striped"
]

with open(DATASET_DIR / "data.yaml", "r") as file:
    config = yaml.safe_load(file)
names = config["names"]

if isinstance(names, list):
    names = dict(enumerate(names))

#Original ID -> new ID
name_to_id = {name: int(i) for i, name in names.items()}

missing = set(SELECTED_SPECIES) - set(name_to_id)
if missing:
    raise ValueError(f"Species not found: {missing}")

id_map = {
    name_to_id[name]: new_id
    for new_id, name in enumerate(SELECTED_SPECIES)
}

# Avoid mixing files from previous subset generations
if TARGET.exists():
    raise FileExistsError(f"Target already exists: {TARGET}")

for split in ["train", "valid", "test"]:
    image_dir = DATASET_DIR / split / "images"
    label_dir = DATASET_DIR / split / "labels"

    output_images = TARGET / split / "images"
    output_labels = TARGET / split / "labels"

    output_images.mkdir(parents=True, exist_ok=True)
    output_labels.mkdir(parents=True, exist_ok=True)

    copied = 0

    for image in image_dir.iterdir():
        if image.suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp"}:
            continue

        label = label_dir / f"{image.stem}.txt"

        if not label.exists():
            continue

        lines = label.read_text().splitlines()
        if not lines:
            continue

        class_ids = [int(line.split()[0]) for line in lines]

        # Skip images containing species outside our subset
        if any(class_id not in id_map for class_id in class_ids):
            continue

        # Remap class IDs, preserve bounding boxes
        new_lines = []

        for line in lines:
            parts = line.split()
            old_id = int(parts[0])
            parts[0] = str(id_map[old_id])
            new_lines.append(" ".join(parts))

        shutil.copy2(image, output_images / image.name)

        (output_labels / label.name).write_text(
            "\n".join(new_lines) + "\n"
        )

        copied += 1

    print(f"{split}: {copied} images copied")

# Create new YOLO configuration
subset_config = {
    "path": str(TARGET.resolve()),
    "train": "train/images",
    "val": "valid/images",
    "test": "test/images",
    "nc": len(SELECTED_SPECIES),
    "names": {
        i: name for i, name in enumerate(SELECTED_SPECIES)
    },
}

with open(TARGET / "data.yaml", "w") as file:
    yaml.safe_dump(subset_config, file, sort_keys=False)

print("Subset created successfully:", TARGET)
