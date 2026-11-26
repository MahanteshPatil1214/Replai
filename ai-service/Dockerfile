FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    HF_HOME=/opt/hf

WORKDIR /app

# CPU-only torch keeps the image (and the download) much smaller.
COPY requirements.txt .
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r requirements.txt

# Bake the embedding model into the image so the container never downloads at runtime.
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('all-MiniLM-L6-v2')"

COPY app ./app
COPY seed ./seed

EXPOSE 8000
CMD ["uvicorn", "app.kb_api:app", "--host", "0.0.0.0", "--port", "8000"]
