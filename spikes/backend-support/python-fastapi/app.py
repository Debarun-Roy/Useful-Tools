"""B0 health-only reference, not a production authentication starter."""
from fastapi import FastAPI
app = FastAPI(title='B0 FastAPI reference', version='0.1.0')

@app.get('/health')
def health():
    return {'status': 'ok', 'version': '0.1.0', 'target': 'python-fastapi'}
