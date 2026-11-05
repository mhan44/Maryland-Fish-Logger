# Maryland Fish Logger
## Overview
Maryland Fish Logger is a lightweight object detection application designed for recreational anglers in Maryland. The application uses a fine-tuned YOLOv8 object detection model to classify species from user-submitted images. From these model predictions, users can log their catches and manually confirm species, as well as save relevant information about their catches.
The application backend is developed with Python and FastAPI, with a planned Android frontend.
## Features
* Identifies 10 common fish species found in inland Maryland
* Outputs the input image with a bounding box, label prediction, and confidence score
* Flexible with manual user confirmation
* Save catch log records with date, timestamp, measurements, bait/lure type, and other notes
## Tech Stack
* Language: Python 3.10
* ML: Ultralytics YOLOv8n, PyTorch
* Backend Framework: FastAPI
* Database: SQLite
* Hardware: NVIDIA Jetson Nano
* Docker Base Image: nvcr.io/nvidia/l4t-ml:r36.2.0-py3
* Python Environment: Conda (YOLO_GPU)
* Frontend: Android Studio, Kotlin
## Model and Data Source
The YOLO model was fine-tuned using a subset of the open source 2025 Fish Finder dataset from Roboflow:
* https://universe.roboflow.com/fish-finder/fish-finder-p1ljc
The selected species/classes being:
* {"Perch - White", "Bowfin", "Crappie - Black", "Bass - Smallmouth", "Catfish - Blue", "Catfish - Flathead", "Bluegill", "Shad - American", "Bass - Largemouth", "Bass - Striped"}
The YOLOv8n model was trained on 50 epochs over the subset DATA_MD_SUBSET, with a batch size of 16 and default image size of 640x640. From the training, the best model weights were selected and used to evaluate the model on the validation set, producing these performance metrics:
* Precision:    0.8570
* Recall:       0.8613
* mAP@50:       0.9136
* mAP@50:95:    0.8041
Indicating strong model fit to the subset of data.
## Backend Structure:
* data_count.py - Data exploration script which listed label counts for the original dataset
* subset.py - Script to create the subset of selected species
* train.py - Fine tunes and trains the YOLO model on the subset of data over 50 epochs
* test.py - Evaluates model performances and calculates metrics and visualizations
* prediction.py - Generates predictions and images with bounding boxes, confidence scores, and class label using the best model from "runs/fish_yolov8n/weights/best.pt"
* main.py - Defines FastAPI endpoints for predictions and logging, initializes SQLite database
* database.py - Defines the SQLite Schema and database operations (initialize, insert, get)
* requirements.txt - Lists required Python packages
## Running backend
From the root repository, run:
* python -m uvicorn backend.main:app --host 0.0.0.0 --port 8000
* With the IP address as the same network address the Jetson Nano device is connected to
The interactive API can be tested at:
* http://0.0.0.0:8000/docs
## Endpoints
* POST - /predict = Upload image
* POST - /catches = Log a catch record
* GET - /catches = Retrieve all logged catch records
* GET - /predictions/filename_url = retrieve output prediction image with bounding box, label, and confidence