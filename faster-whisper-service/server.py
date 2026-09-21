"""
FinPilot 语音转写微服务 — Faster-Whisper
提供 POST /transcribe 接口，接收音频文件，返回转写文本。
启动：python server.py（默认端口 8766）
首次启动会自动下载 faster-whisper-small 模型（约 500MB，CPU友好），请保持网络畅通。
"""
import os
import tempfile
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

MODEL_SIZE = os.getenv("WHISPER_MODEL", "small")
DEVICE = os.getenv("WHISPER_DEVICE", "cpu")
COMPUTE_TYPE = os.getenv("WHISPER_COMPUTE", "int8")  # int8 适合 CPU
PORT = int(os.getenv("WHISPER_PORT", "8766"))

model = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model
    from faster_whisper import WhisperModel
    print(f"正在加载 Faster-Whisper 模型 {MODEL_SIZE}（{DEVICE}/{COMPUTE_TYPE}）...")
    model = WhisperModel(MODEL_SIZE, device=DEVICE, compute_type=COMPUTE_TYPE)
    print(f"模型加载完成，服务就绪 — 端口 {PORT}")
    yield


app = FastAPI(title="FinPilot 语音转写服务", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health():
    return {"status": "ok", "model": MODEL_SIZE, "device": DEVICE}


@app.post("/transcribe")
async def transcribe(file: UploadFile = File(...)):
    """接收音频文件（mp3/wav/m4a/ogg），返回转写文本。"""
    if model is None:
        raise HTTPException(status_code=503, detail="模型尚未加载完成")

    # 保存临时文件
    suffix = os.path.splitext(file.filename or "audio.wav")[1] or ".wav"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        content = await file.read()
        if not content:
            raise HTTPException(status_code=400, detail="上传文件为空")
        tmp.write(content)
        tmp_path = tmp.name

    try:
        # 转写（中文自动检测，beam_size=5 提高准确率）
        segments, info = model.transcribe(
            tmp_path,
            beam_size=5,
            language="zh",
            vad_filter=True,        # 过滤静音段
            vad_parameters=dict(
                min_silence_duration_ms=500,
            ),
        )

        text = " ".join(seg.text.strip() for seg in segments)

        return {
            "text": text,
            "language": info.language,
            "duration_seconds": round(info.duration, 1),
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"转写失败: {str(e)}")
    finally:
        try:
            os.unlink(tmp_path)
        except OSError:
            pass


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=PORT)
