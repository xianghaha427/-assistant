FROM python:3.12-slim
WORKDIR /app
RUN pip install --no-cache-dir faster-whisper fastapi uvicorn python-multipart -q
COPY server.py .
# 预下载 small 模型到镜像中
RUN python -c "from faster_whisper import WhisperModel; WhisperModel('small', device='cpu', compute_type='int8')"
EXPOSE 8766
CMD ["python", "server.py"]
