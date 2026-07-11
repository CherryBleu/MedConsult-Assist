# MedConsult 本地 Embedding 服务镜像
#
# 提供 OpenAI 兼容的 /v1/embeddings 接口（BAAI/bge-small-zh-v1.5，512 维）。
# 随 docker compose up 自动拉起，ai-service 的 EMBEDDING_BASE_URL 指向 http://localhost:7997/v1。
#
# 构建上下文为本目录（infra/embedding-server/）。
# 首次构建较慢（torch CPU ~700MB + sentence-transformers），之后走缓存。
# 模型文件首次启动从 HuggingFace 下载（~95MB），缓存在命名卷内避免重复下载。

FROM python:3.11-slim

# 系统依赖（sentence-transformers 需要 build 工具链兜底，CPU 版 torch 无需 CUDA）
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 先装依赖（利用 Docker 层缓存：requirements 变化才重装）
# torch 走 CPU 专用 index，避免拉到 CUDA 版（镜像会大 2GB+）
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch \
    && pip install --no-cache-dir flask==3.0.* sentence-transformers==3.0.* numpy==1.26.*

COPY app.py /app/app.py

# 模型缓存卷（HuggingFace 下载产物持久化，避免容器重建后重下）
VOLUME ["/root/.cache/huggingface"]

ENV EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5 \
    EMBEDDING_PORT=7997 \
    EMBEDDING_DEVICE=cpu \
    HF_ENDPOINT=https://hf-mirror.com

EXPOSE 7997

# 健康检查：/health 返回 200 即就绪（模型已加载）
HEALTHCHECK --interval=15s --timeout=10s --retries=10 --start-period=120s \
    CMD curl -sf http://localhost:7997/health || exit 1

CMD ["python", "app.py"]
