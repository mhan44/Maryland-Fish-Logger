import os
from pathlib import Path
import random
import torch
import yaml
from ultralytics import YOLO
import matplotlib.pyplot as plt
from PIL import Image

def visualize(metrics):

    evaluation_dir = str(metrics.save_dir)
    
    plot_files = [
        "confusion_matrix_normalized.png",
        "BoxPR_curve.png",
        "BoxF1_curve.png",
        "val_batch0_labels.jpg",
        "val_batch0_pred.jpg",
    ]
    
    for filename in plot_files:
        filepath = os.path.join(evaluation_dir, filename)
    
        if os.path.isfile(filepath):
            with Image.open(filepath) as image:
                plt.figure(figsize=(14, 10))
                plt.imshow(image)
                plt.title(filename)
                plt.axis("off")
                plt.show()

#Repo directory
PROJECT_DIR = Path(__file__).resolve().parent.parent

#Root dataset directory
DATASET_DIR = Path("../Fish Finder.v17i.yolo26").resolve()

#Best model weights path
BEST_MODEL_PATH = PROJECT_DIR / "runs/fish_yolov8n/weights/best.pt"

#yaml path
original_yaml = os.path.join(DATASET_DIR, "data.yaml") #join the path to our .yaml file

IMAGE_SIZE = 640
BATCH_SIZE=32
SEED = 1
DEVICE = 0 if torch.cuda.is_available() else "cpu"

#===========================================================================================================
best_model = YOLO(BEST_MODEL_PATH)

with open(original_yaml, "r", encoding="utf-8") as file:
    original_config = yaml.safe_load(file)

metrics = best_model.val(
    data=original_yaml,
    split="test",
    imgsz=IMAGE_SIZE,
    batch=BATCH_SIZE,
    device=DEVICE,
    workers=0,
    plots=True,
    project=str(PROJECT_DIR / "runs"),
    name="fish_test",
)

print("\nFinal test metrics")
print(f"Precision:    {metrics.box.mp:.4f}")
print(f"Recall:       {metrics.box.mr:.4f}")
print(f"mAP@50:       {metrics.box.map50:.4f}")
print(f"mAP@50:95:    {metrics.box.map:.4f}")

visualize(metrics)