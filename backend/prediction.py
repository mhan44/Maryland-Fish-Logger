from pathlib import Path
import sys
from ultralytics import YOLO
#from PIL import Image

#Project directory
PROJECT_DIR = Path(__file__).resolve().parent.parent

#Output directory
OUTPUT_DIR = PROJECT_DIR / "backend/sample_output"

#Model path
MODEL_PATH = PROJECT_DIR / "runs/fish_yolov8n/weights/best.pt"
model = YOLO(str(MODEL_PATH)) #extract best model
#=================================================================================
def predict_fish(image_path):
    results = model.predict(
        source=str(image_path),
        conf=0.3,
        imgsz=640,
        verbose=True
    )
    
    result = results[0]

    #Image w/ BB+Label+Confidence
    output_path = OUTPUT_DIR / f"{Path(image_path).stem}_pred.jpg"
    result.save(filename=str(output_path))

    #If the model can't predict a species above the confidence threshold, return unidentified
    if len(result.boxes) == 0:
        return {
            "species": None,
            "confidence": None,
            "status": "unidentified",
            "image_conf": str(output_path)
        }

    #Else select predicted species with the highest confidence
    index = int(result.boxes.conf.argmax().item())

    class_id = int(result.boxes.cls[index].item())
    confidence = float(result.boxes.conf[index].item())
    bbox = result.boxes.xyxy[index].tolist()
    return {
        "species": result.names[class_id],
        "confidence": round(confidence, 4),
        "bounding_box": bbox,
        "status": "suggested",
        "image_conf": str(output_path)
    }


if __name__ == "__main__":
    image_path = sys.argv[1]
    prediction = predict_fish(image_path)
    print(prediction)
