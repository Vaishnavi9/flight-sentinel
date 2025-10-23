from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(
    title="Flight Sentinel ML Service",
    version="1.0.0",
    description="Simple prediction stub for delay probability."
)

class PredictRequest(BaseModel):
    flight: str
    origin: str
    destination: str
    scheduled_dep_iso: str

class PredictResponse(BaseModel):
    probability_delay_over_30min: float

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    # TODO: load model and compute. For now, return a deterministic stub.
    prob = 0.42 if req.origin and req.destination else 0.1
    return PredictResponse(probability_delay_over_30min=prob)
