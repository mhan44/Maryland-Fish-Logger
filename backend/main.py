from pathlib import Path
from tempfile import TemporaryDirectory
from uuid import uuid4

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.staticfiles import StaticFiles
from backend.prediction import predict_fish, OUTPUT_DIR

app = FastAPI(title="Maryland Fish Logger API")

#Prediction images accessible via HTTP
app.mount("/predictions", StaticFiles(directory=str(OUTPUT_DIR)), name="predictions")

@app.get("/")
def home():
    return {"message": "Maryland Fish Logger API is running"}


@app.post("/predict")
async def predict(file: UploadFile = File(...)):

    #Acceptable image formats
    extensions = {
        "image/jpeg": ".jpg",
        "image/png": ".png",
        "image/webp": ".webp"
    }

    if file.content_type not in extensions:
        raise HTTPException(status_code=400, detail="Unsupported image format")

    #Save uploaded image temporarily
    with TemporaryDirectory() as temp_dir:
        image_path = Path(temp_dir) / f"{uuid4().hex}{extensions[file.content_type]}"
        image_path.write_bytes(await file.read())

        #Model prediction
        prediction = predict_fish(image_path)

    #Return a URL instead of the internal file path
    image_conf_path = Path(prediction.pop("image_conf"))
    prediction["image_url"] = f"/predictions/{image_conf_path.name}"

    return prediction
