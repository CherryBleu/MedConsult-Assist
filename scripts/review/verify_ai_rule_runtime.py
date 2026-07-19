from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import socket
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


DEFAULT_BASE_URL = "http://127.0.0.1:8086"
RAG_PATHS = (
    "/api/v1/ai/rag/readiness?refresh=true",
    "/api/v1/ai/rag/probes",
)
FAILURE_PATTERNS = (
    "Unknown column 'enabled'",
    "RiskRuleEngine 降级",
)


@dataclass(frozen=True)
class EndpointResult:
    url: str
    status: int
    message: str


def build_urls(base_url: str) -> list[str]:
    base = base_url.rstrip("/")
    return [f"{base}{path}" for path in RAG_PATHS]


def parse_headers(values: list[str]) -> dict[str, str]:
    headers: dict[str, str] = {}
    for value in values:
        if ":" not in value:
            raise ValueError(f"header must be NAME: VALUE, got: {value}")
        name, header_value = value.split(":", 1)
        name = name.strip()
        if not name:
            raise ValueError(f"header name is empty: {value}")
        headers[name] = header_value.strip()
    return headers


def check_tcp(base_url: str, timeout_seconds: float) -> None:
    parsed = urlparse(base_url)
    host = parsed.hostname or "127.0.0.1"
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    try:
        with socket.create_connection((host, port), timeout=timeout_seconds):
            return
    except OSError as exc:
        raise RuntimeError(f"ai-service is not reachable at {host}:{port}: {exc}") from exc


def probe_endpoint(url: str, headers: dict[str, str], timeout_seconds: float) -> EndpointResult:
    request = Request(url, headers=headers, method="GET")
    try:
        with urlopen(request, timeout=timeout_seconds) as response:
            return EndpointResult(url, response.status, "OK")
    except HTTPError as exc:
        if exc.code in (401, 403):
            return EndpointResult(url, exc.code, "reachable; authentication required")
        return EndpointResult(url, exc.code, f"unexpected HTTP status {exc.code}")
    except URLError as exc:
        raise RuntimeError(f"failed to call {url}: {exc.reason}") from exc


def endpoint_status_is_acceptable(status: int) -> bool:
    return 200 <= status < 300 or status in (401, 403)


def validate_timeout(timeout_seconds: float) -> None:
    if timeout_seconds <= 0:
        raise ValueError("timeout must be greater than 0")


def scan_log_file(path: Path, patterns: tuple[str, ...] = FAILURE_PATTERNS) -> list[str]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    findings: list[str] = []
    for line_no, line in enumerate(text.splitlines(), start=1):
        for pattern in patterns:
            if pattern in line:
                findings.append(f"{path}:{line_no}: {line.strip()}")
                break
    return findings


def self_check() -> int:
    urls = build_urls(DEFAULT_BASE_URL)
    if urls != [
        "http://127.0.0.1:8086/api/v1/ai/rag/readiness?refresh=true",
        "http://127.0.0.1:8086/api/v1/ai/rag/probes",
    ]:
        print("self-check failed: unexpected endpoint URLs", file=sys.stderr)
        return 1
    try:
        parse_headers(["Authorization: Bearer token"])
    except ValueError as exc:
        print(f"self-check failed: {exc}", file=sys.stderr)
        return 1
    print("self-check passed")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Read-only ai-service RAG rule runtime smoke check.",
    )
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help=f"default: {DEFAULT_BASE_URL}")
    parser.add_argument("--timeout", type=float, default=3.0, help="TCP/HTTP timeout seconds")
    parser.add_argument(
        "--header",
        action="append",
        default=[],
        help="HTTP header for protected endpoints, format 'Name: value'. Can be repeated.",
    )
    parser.add_argument("--log-file", type=Path, help="Optional ai-service log file to scan")
    parser.add_argument("--self-check", action="store_true", help="Run offline checks and exit")
    args = parser.parse_args(argv)

    if args.self_check:
        return self_check()

    try:
        validate_timeout(args.timeout)
        headers = parse_headers(args.header)
        check_tcp(args.base_url, args.timeout)
        endpoint_results = [probe_endpoint(url, headers, args.timeout) for url in build_urls(args.base_url)]
    except (RuntimeError, ValueError) as exc:
        print(str(exc), file=sys.stderr)
        return 1

    failed = False
    for result in endpoint_results:
        print(f"{result.status} {result.url} - {result.message}")
        if not endpoint_status_is_acceptable(result.status):
            failed = True

    if args.log_file:
        if not args.log_file.exists():
            print(f"log file does not exist: {args.log_file}", file=sys.stderr)
            return 1
        findings = scan_log_file(args.log_file)
        if findings:
            print("Risk rule fallback evidence found in log:", file=sys.stderr)
            for finding in findings:
                print(finding, file=sys.stderr)
            failed = True
        else:
            print(f"log scan passed: {args.log_file}")

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
