from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
import sys


SKIP_VALUES = {"", "-", "—", "N/A", "n/a", "NA", "na", "无"}
METHOD_MAPPING = {
    "GET": "GetMapping",
    "POST": "PostMapping",
    "PUT": "PutMapping",
    "PATCH": "PatchMapping",
    "DELETE": "DeleteMapping",
}


@dataclass(frozen=True)
class ContractRow:
    domain: str
    method: str
    public_path: str
    docs_needle: str
    frontend: str
    frontend_needle: str
    backend: str
    controller_prefix: str
    method_suffix: str
    permission: str
    table: str
    test: str
    status: str


def _clean_cell(cell: str) -> str:
    return cell.strip().strip("`").replace("&vert;", "|")


def _is_skip(value: str) -> bool:
    return value.strip() in SKIP_VALUES


def parse_matrix(markdown: str) -> list[ContractRow]:
    rows: list[ContractRow] = []
    for line in markdown.splitlines():
        stripped = line.strip()
        if not stripped.startswith("|") or "---" in stripped:
            continue
        cells = [_clean_cell(cell) for cell in stripped.strip("|").split("|")]
        if not cells or cells[0] == "Domain":
            continue
        if len(cells) != 13:
            continue
        rows.append(ContractRow(*cells))
    return rows


def _read_text(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def _contains(path: Path, needle: str) -> bool:
    return _is_skip(needle) or needle in _read_text(path)


def _contains_normalized(text: str, needle: str) -> bool:
    if _is_skip(needle):
        return True
    return re.sub(r"\s+", "", needle) in re.sub(r"\s+", "", text)


def _mapping_found(text: str, method: str, suffix: str) -> bool:
    method = method.upper()
    mapping = METHOD_MAPPING.get(method)
    if mapping is None:
        return False

    if _is_skip(suffix) or suffix == "ROOT":
        return f"@{mapping}" in text

    escaped_suffix = re.escape(suffix)
    direct_pattern = re.compile(rf"@{mapping}\s*\([^)]*{escaped_suffix}[^)]*\)", re.DOTALL)
    if direct_pattern.search(text):
        return True

    request_mapping_pattern = re.compile(
        rf"@RequestMapping\s*\([^)]*{escaped_suffix}[^)]*RequestMethod\.{method}[^)]*\)",
        re.DOTALL,
    )
    return bool(request_mapping_pattern.search(text))


def _split_multi(value: str) -> list[str]:
    if _is_skip(value):
        return []
    return [part.strip() for part in re.split(r"[,，、]", value) if part.strip()]


def _table_corpus(root: Path) -> str:
    chunks = [_read_text(root / "docs/数据库设计文档.md")]
    backend = root / "backend"
    if backend.exists():
        for pattern in ("**/*.sql", "**/*.xml", "**/*.java", "**/*.yml", "**/*.yaml"):
            for path in backend.glob(pattern):
                chunks.append(_read_text(path))
    return "\n".join(chunks)


def verify_rows(root: Path, rows: list[ContractRow]) -> list[str]:
    errors: list[str] = []
    docs = root / "docs/接口文档.md"
    table_corpus = _table_corpus(root)

    for row in rows:
        label = f"{row.domain} {row.method} {row.public_path}"

        if row.method.upper() not in METHOD_MAPPING:
            errors.append(f"{label}: Method 不受支持 {row.method}")
        if not row.public_path.startswith("/"):
            errors.append(f"{label}: Public Path 必须以 / 开头")

        if not _contains(docs, row.docs_needle):
            errors.append(f"{label}: docs 未命中 {row.docs_needle}")

        if not _is_skip(row.frontend):
            frontend_path = root / row.frontend
            if not frontend_path.exists():
                errors.append(f"{label}: frontend 文件不存在 {row.frontend}")
            elif not _contains(frontend_path, row.frontend_needle):
                errors.append(f"{label}: frontend 未命中 {row.frontend_needle}")

        if not _is_skip(row.backend):
            backend_path = root / row.backend
            backend_text = _read_text(backend_path)
            if not backend_path.exists():
                errors.append(f"{label}: backend 文件不存在 {row.backend}")
            else:
                if not _is_skip(row.controller_prefix) and row.controller_prefix not in backend_text:
                    errors.append(f"{label}: backend 未命中 Controller Prefix {row.controller_prefix}")
                if not _mapping_found(backend_text, row.method, row.method_suffix):
                    errors.append(f"{label}: backend method-level mapping 未命中 {row.method} {row.method_suffix}")
                if not _contains_normalized(backend_text, row.permission):
                    errors.append(f"{label}: backend 权限未命中 {row.permission}")

        for table in _split_multi(row.table):
            if table not in table_corpus:
                errors.append(f"{label}: 数据表未命中 {table}")

        for test_path in _split_multi(row.test):
            if not (root / test_path).exists():
                errors.append(f"{label}: 测试文件不存在 {test_path}")

    return errors


def main(argv: list[str] | None = None) -> int:
    args = argv if argv is not None else sys.argv[1:]
    root = Path(args[0]).resolve() if args else Path.cwd()
    matrix = root / "docs/全栈契约矩阵.md"
    if not matrix.exists():
        print(f"契约矩阵不存在: {matrix}", file=sys.stderr)
        return 1

    rows = parse_matrix(matrix.read_text(encoding="utf-8", errors="ignore"))
    errors = verify_rows(root, rows)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print(f"契约矩阵检查通过: {len(rows)} rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
