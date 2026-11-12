from pathlib import Path
from tempfile import TemporaryDirectory
from uuid import uuid4

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.staticfiles import StaticFiles
from backend.prediction import predict_fish, OUTPUT_DIR

from datetime import date
from pydantic import BaseModel, Field
from backend.database import init_db, save_catch, get_catches, update_catch, delete_catch

app = FastAPI(title="Maryland Fish Logger API")
init_db()

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


# Structure of a catch record
class Catch(BaseModel):
    species: str | None = None
    predicted_species: str | None = None
    confidence: float | None = Field(default=None, ge=0, le=1)
    length_inches: float | None = Field(default=None, gt=0)
    weight_lbs: float | None = Field(default=None, gt=0)
    date_caught: date | None = None
    location: str | None = None
    lure: str | None = None
    notes: str | None = None
    image_url: str | None = None

#Log catch
@app.post("/catches", status_code=201)
def create_catch(catch: Catch):
    catch_data = catch.model_dump(mode="json")
    catch_id = save_catch(catch_data)

    return {
        "message": "Catch saved successfully",
        "id": catch_id
    }


#Retrieve catches
@app.get("/catches")
def list_catches():
    return get_catches()


#Update an existing catch
@app.put("/catches/{catch_id}")
def update_existing_catch(catch_id: int, catch: Catch):

    #Convert validated Pydantic data into a dictionary
    catch_data = catch.model_dump(
        mode="json",
        exclude_unset=True
    )

    if not catch_data:
        raise HTTPException(
            status_code=400,
            detail="No fields provided for update"
        )

    updated = update_catch(catch_id, catch_data)

    if not updated:
        raise HTTPException(
            status_code=404,
            detail="Catch not found"
        )

    return {
        "message": "Catch updated successfully",
        "id": catch_id
    }


#Delete an existing catch
@app.delete("/catches/{catch_id}")
def delete_existing_catch(catch_id: int):

    deleted = delete_catch(catch_id)

    if not deleted:
        raise HTTPException(
            status_code=404,
            detail="Catch not found"
        )

    return {
        "message": "Catch deleted successfully",
        "id": catch_id
    }
