import numpy as np
import os
from pathlib import Path
import torch
import yaml
from ultralytics import YOLO

#Current directory
BACKEND_DIR = Path(__file__).resolve().parent

#Repository directory
PROJECT_DIR = BACKEND_DIR.parent

#Root dataset directory
DATASET_DIR = Path("../Fish Finder.v17i.yolo26").resolve()

#Train/Validation/Test dataset directories
TRAIN_DATA_DIR = os.path.abspath("../Fish Finder.v17i.yolo26/train/images/") #load filepath to training images
TRAIN_LABEL_DIR = os.path.abspath("../Fish Finder.v17i.yolo26/train/labels/") #load filepath to training labels

VAL_DATA_DIR = os.path.abspath("../Fish Finder.v17i.yolo26/valid/images/") #load filepath to validation images
VAL_LABEL_DIR = os.path.abspath("../Fish Finder.v17i.yolo26/valid/labels/") #load filepath to validation labels

TEST_DATA_DIR = os.path.abspath("../Fish Finder.v17i.yolo26/test/images/") #load filepath to test images
TEST_LABEL_DIR = os.path.abspath("../Fish Finder.v17i.yolo26/test/labels/") #load filepath to test labels

original_yaml = os.path.join(DATASET_DIR, "data.yaml") #join the path to our .yaml file

#Read the file
with open(original_yaml, "r", encoding="utf-8") as file:
    original_config = yaml.safe_load(file)

names = original_config["names"]
print("Number of fish species:", len(names))

#================================================================================================================================
#Since we have a provided validation and training set, there's no need to partition the training data any further 
DEVICE = 0 if torch.cuda.is_available() else "cpu"

MODEL_WEIGHTS = "yolov8n.pt" #choose the model we're going to use
EPOCHS = 5 #5 full passes over the training data through the model
IMAGE_SIZE = 640 #bounding box dimensions: 640x640
BATCH_SIZE = 32 #allocates 32 locations that are 640x640
SEED = 1 #random seed selection

model = YOLO(MODEL_WEIGHTS) #set weights that we selected

model.train(
    data=str(original_yaml),
    epochs=EPOCHS,
    imgsz=IMAGE_SIZE,
    batch=BATCH_SIZE,
    device=DEVICE,
    workers=0,
    seed=SEED,
    project=str(PROJECT_DIR / "runs"),
    name="fish_yolov8n",
    plots=True,
)