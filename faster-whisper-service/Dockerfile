FROM python:3.12-slim
WORKDIR /app
RUN pip install --no-cache-dir faster-whisper fastapi uvicorn python-multipart -q
COPY server.py .
# 模型运行时按需下载，避免首次构建因 Hugging Face 网络波动失败
EXPOSE 8766
CMD ["python", "server.py"]
