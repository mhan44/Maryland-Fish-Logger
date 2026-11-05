from pathlib import Path
from collections import Counter
import yaml

PROJECT_DIR = Path(__file__).resolve().parent.parent
DATASET_DIR = PROJECT_DIR / "Fish Finder.v17i.yolo26"

with (DATASET_DIR / "data.yaml").open("r") as file:
    config = yaml.safe_load(file)

# Support both list and dictionary class mappings
names = config["names"]

if isinstance(names, list):
    names = dict(enumerate(names))
else:
    names = {int(k): v for k, v in names.items()}

# Count annotated instances and images per species
splits = ["train", "valid", "test"]

counts = {split: Counter() for split in splits}
image_counts = {split: Counter() for split in splits}

for split in splits:
    label_dir = DATASET_DIR / split / "labels"

    for label_path in label_dir.rglob("*.txt"):
        classes_in_image = set()

        for line in label_path.read_text().splitlines():
            if not line.strip():
                continue

            class_id = int(line.split()[0])

            counts[split][class_id] += 1
            classes_in_image.add(class_id)

        for class_id in classes_in_image:
            image_counts[split][class_id] += 1

print(f"{'Species':35} {'Train':>7} {'Val':>7} {'Test':>7} {'Train Img':>10}")
print("-" * 75)

for class_id in sorted(
    names, key=lambda k: counts["train"][k], reverse=True
):
    print(
        f"{names[class_id]:35} "
        f"{counts['train'][class_id]:7} "
        f"{counts['valid'][class_id]:7} "
        f"{counts['test'][class_id]:7} "
        f"{image_counts['train'][class_id]:10}"
    )
