"""
MedConsult 本地 Embedding 服务（轻量化）

提供 OpenAI 兼容的 /v1/embeddings 接口，供 ai-service 和 importer 调用。
默认模型：BAAI/bge-small-zh-v1.5（512 维，95MB，C-MTEB 同级别最优中文 embedding）。

架构定位：
  - 这是 ai-service RAG 链路的 embedding 组件，与 LLM（阿里云百炼）完全解耦
  - ai-service 的 OpenAiCompatibleClient 和 importer 的 OpenAiEmbeddingClient
    都调标准 {base-url}/embeddings（OpenAI 协议），本服务直接兼容
  - 首次启动自动从 HuggingFace 下载模型（~95MB），缓存在 ~/.cache/huggingface/

启动：
  pip install flask sentence-transformers torch
  python app.py                          # 默认 0.0.0.0:7997, bge-small-zh-v1.5
  EMBEDDING_MODEL=BAAI/bge-base-zh-v1.5 EMBEDDING_PORT=7998 python app.py

环境变量：
  EMBEDDING_MODEL   模型名（默认 BAAI/bge-small-zh-v1.5）
  EMBEDDING_PORT    端口（默认 7997）
  EMBEDDING_DEVICE  设备 cpu/cuda（默认 cpu；容器化部署无 GPU）
  HF_ENDPOINT       HuggingFace 镜像（国内可设 https://hf-mirror.com）

部署：
  - 容器化：docker compose up 自动拉起（见 Dockerfile + infra/docker-compose.yml embedding 服务）
  - 本机直跑：pip install -r requirements.txt && python app.py（有 GPU 设 EMBEDDING_DEVICE=cuda）
"""

import os
import time
import logging

import numpy as np
from flask import Flask, request, jsonify
from sentence_transformers import SentenceTransformer

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("embedding-server")

# ---------------------------------------------------------------------------
# 配置
# ---------------------------------------------------------------------------
MODEL_NAME = os.environ.get("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
PORT = int(os.environ.get("EMBEDDING_PORT", "7997"))
# 默认 cpu（容器内无 GPU）；本机有 GPU 时设 EMBEDDING_DEVICE=cuda 加速
DEVICE = os.environ.get("EMBEDDING_DEVICE", "cpu")

app = Flask(__name__)

# 全局模型实例（懒加载，首次请求时加载）
_model: SentenceTransformer | None = None
_model_dim: int = 0


def get_model() -> SentenceTransformer:
    global _model, _model_dim
    if _model is None:
        log.info("加载 embedding 模型: %s (device=%s) ...", MODEL_NAME, DEVICE)
        t0 = time.time()
        _model = SentenceTransformer(MODEL_NAME, device=DEVICE)
        _model_dim = _model.get_sentence_embedding_dimension()
        log.info(
            "模型加载完成: %s, 维度=%d, 耗时=%.1fs",
            MODEL_NAME, _model_dim, time.time() - t0,
        )
    return _model


@app.route("/health", methods=["GET"])
@app.route("/", methods=["GET"])
def health():
    """健康检查端点"""
    loaded = _model is not None
    return jsonify({
        "status": "ok",
        "model": MODEL_NAME,
        "loaded": loaded,
        "dimension": _model_dim,
        "device": DEVICE,
    })


@app.route("/v1/embeddings", methods=["POST"])
@app.route("/embeddings", methods=["POST"])
def embeddings():
    """
    OpenAI 兼容的 embeddings 接口。

    请求体（OpenAI 标准格式）：
    {
        "model": "BAAI/bge-small-zh-v1.5",  // 可选，忽略（只用一个模型）
        "input": "文本" 或 ["文本1", "文本2"]  // 必填
    }

    响应（OpenAI 标准格式）：
    {
        "object": "list",
        "model": "BAAI/bge-small-zh-v1.5",
        "data": [
            {"object": "embedding", "index": 0, "embedding": [0.01, -0.02, ...]}
        ],
        "usage": {"prompt_tokens": N, "total_tokens": N}
    }
    """
    body = request.get_json(silent=True) or {}

    # 解析 input（支持字符串和数组两种形式，与 OpenAI 一致）
    raw_input = body.get("input")
    if raw_input is None:
        return jsonify({"error": {"message": "field 'input' is required", "type": "invalid_request_error"}}), 400

    if isinstance(raw_input, str):
        texts = [raw_input]
    elif isinstance(raw_input, list):
        texts = [str(t) for t in raw_input]
        if not texts:
            return jsonify({"error": {"message": "field 'input' cannot be empty", "type": "invalid_request_error"}}), 400
    else:
        return jsonify({"error": {"message": "field 'input' must be string or array", "type": "invalid_request_error"}}), 400

    # 截断超长文本（bge-small-zh-v1.5 max_seq_length=512）
    model = get_model()
    max_tokens = getattr(model, "max_seq_length", 512) or 512
    # 中文按 1 字 ≈ 1 token 粗估，预留余量
    max_chars = max_tokens * 2
    truncated_texts = [t[:max_chars] if len(t) > max_chars else t for t in texts]

    t0 = time.time()
    try:
        # batch encode，normalize=True 让向量归一化（配合 Milvus COSINE）
        vectors = model.encode(
            truncated_texts,
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        )
    except Exception as e:
        log.error("embedding 失败: %s", e, exc_info=True)
        return jsonify({"error": {"message": str(e), "type": "internal_error"}}), 500

    elapsed_ms = int((time.time() - t0) * 1000)
    log.info(
        "embed: count=%d dims=%d elapsed=%dms",
        len(texts), vectors.shape[1], elapsed_ms,
    )

    # 组装 OpenAI 标准响应
    data = []
    total_tokens = 0
    for i, vec in enumerate(vectors):
        data.append({
            "object": "embedding",
            "index": i,
            "embedding": np.round(vec, 8).astype(float).tolist(),
        })
        total_tokens += len(truncated_texts[i])

    return jsonify({
        "object": "list",
        "model": MODEL_NAME,
        "data": data,
        "usage": {"prompt_tokens": total_tokens, "total_tokens": total_tokens},
    })


if __name__ == "__main__":
    # 预加载模型（避免第一个请求慢）
    log.info("=" * 60)
    log.info("MedConsult Embedding Server")
    log.info("  模型: %s", MODEL_NAME)
    log.info("  端口: %d", PORT)
    log.info("  设备: %s", DEVICE)
    log.info("=" * 60)

    get_model()

    log.info("服务启动: http://0.0.0.0:%d/v1/embeddings", PORT)
    # threaded=True 让 Flask 支持并发请求（importer 批量调用时有用）
    app.run(host="0.0.0.0", port=PORT, threaded=True)
