from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
from pathlib import Path
import subprocess
import sys


DEFAULT_SQL_PATH = Path("backend/ai-service/src/main/resources/db/upgrade-ai-architecture-20260710.sql")


@dataclass(frozen=True)
class LineEndingCounts:
    bytes: int
    crlf: int
    lf_total: int
    lf_only: int
    cr_only: int


@dataclass(frozen=True)
class SqlEolReport:
    head_sha256: str
    worktree_raw_sha256: str
    worktree_normalized_sha256: str
    head_git_blob_sha1: str
    worktree_raw_git_blob_sha1: str
    worktree_normalized_git_blob_sha1: str
    head_counts: LineEndingCounts
    worktree_counts: LineEndingCounts
    raw_equal: bool
    normalized_equal: bool

    @property
    def line_ending_only(self) -> bool:
        return not self.raw_equal and self.normalized_equal


def git_blob_sha1(data: bytes) -> str:
    header = b"blob " + str(len(data)).encode("ascii") + b"\0"
    return hashlib.sha1(header + data).hexdigest()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def count_line_endings(data: bytes) -> LineEndingCounts:
    crlf = data.count(b"\r\n")
    lf_total = data.count(b"\n")
    cr_only = data.count(b"\r") - crlf
    return LineEndingCounts(
        bytes=len(data),
        crlf=crlf,
        lf_total=lf_total,
        lf_only=lf_total - crlf,
        cr_only=cr_only,
    )


def normalize_crlf_to_lf(data: bytes) -> bytes:
    return data.replace(b"\r\n", b"\n")


def analyze_bytes(head_bytes: bytes, worktree_bytes: bytes) -> SqlEolReport:
    worktree_normalized = normalize_crlf_to_lf(worktree_bytes)
    return SqlEolReport(
        head_sha256=sha256(head_bytes),
        worktree_raw_sha256=sha256(worktree_bytes),
        worktree_normalized_sha256=sha256(worktree_normalized),
        head_git_blob_sha1=git_blob_sha1(head_bytes),
        worktree_raw_git_blob_sha1=git_blob_sha1(worktree_bytes),
        worktree_normalized_git_blob_sha1=git_blob_sha1(worktree_normalized),
        head_counts=count_line_endings(head_bytes),
        worktree_counts=count_line_endings(worktree_bytes),
        raw_equal=head_bytes == worktree_bytes,
        normalized_equal=head_bytes == worktree_normalized,
    )


def repo_root() -> Path:
    root = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()
    return Path(root)


def repo_relative_path(path: Path, root: Path) -> str:
    absolute_path = path if path.is_absolute() else root / path
    return absolute_path.resolve().relative_to(root.resolve()).as_posix()


def read_git_blob(git_ref: str, relative_path: str, root: Path) -> bytes:
    return subprocess.check_output(["git", "cat-file", "blob", f"{git_ref}:{relative_path}"], cwd=root)


def analyze_file(path: Path, git_ref: str = "HEAD") -> tuple[str, SqlEolReport]:
    root = repo_root()
    relative_path = repo_relative_path(path, root)
    worktree_path = root / relative_path
    worktree_bytes = worktree_path.read_bytes()
    head_bytes = read_git_blob(git_ref, relative_path, root)
    return relative_path, analyze_bytes(head_bytes, worktree_bytes)


def format_counts(counts: LineEndingCounts) -> str:
    return (
        f"bytes={counts.bytes} crlf={counts.crlf} "
        f"lf_total={counts.lf_total} lf_only={counts.lf_only} cr_only={counts.cr_only}"
    )


def render_report(relative_path: str, git_ref: str, report: SqlEolReport) -> str:
    if report.raw_equal:
        result = "PASS raw content matches git blob"
    elif report.line_ending_only:
        result = "PASS raw content differs only by CRLF line endings after normalization"
    else:
        result = "FAIL normalized worktree content differs from git blob"

    return "\n".join(
        [
            f"path={relative_path}",
            f"git_ref={git_ref}",
            f"head_sha256={report.head_sha256}",
            f"worktree_raw_sha256={report.worktree_raw_sha256}",
            f"worktree_crlf_to_lf_sha256={report.worktree_normalized_sha256}",
            f"head_git_blob_sha1={report.head_git_blob_sha1}",
            f"worktree_raw_git_blob_sha1={report.worktree_raw_git_blob_sha1}",
            f"worktree_crlf_to_lf_git_blob_sha1={report.worktree_normalized_git_blob_sha1}",
            f"head_line_endings={format_counts(report.head_counts)}",
            f"worktree_line_endings={format_counts(report.worktree_counts)}",
            f"raw_equal={str(report.raw_equal).lower()}",
            f"normalized_equal={str(report.normalized_equal).lower()}",
            f"line_ending_only={str(report.line_ending_only).lower()}",
            f"result={result}",
        ]
    )


def self_check() -> int:
    line_ending_only = analyze_bytes(b"SELECT 1;\nSELECT 2;\n", b"SELECT 1;\r\nSELECT 2;\n")
    semantic_diff = analyze_bytes(b"SELECT 1;\n", b"SELECT 2;\r\n")
    if not line_ending_only.line_ending_only:
        print("self-check failed: expected line-ending-only diff", file=sys.stderr)
        return 1
    if semantic_diff.normalized_equal:
        print("self-check failed: expected semantic diff after normalization", file=sys.stderr)
        return 1
    if git_blob_sha1(b"hello\n") != "ce013625030ba8dba906f756967f9e9ca394464a":
        print("self-check failed: unexpected git blob hash", file=sys.stderr)
        return 1
    print("self-check passed")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Read-only SQL worktree-vs-HEAD line-ending and semantic diff verifier.",
    )
    parser.add_argument("--path", type=Path, default=DEFAULT_SQL_PATH, help=f"default: {DEFAULT_SQL_PATH}")
    parser.add_argument("--git-ref", default="HEAD", help="Git ref to compare against; default: HEAD")
    parser.add_argument("--self-check", action="store_true", help="Run offline checks and exit")
    args = parser.parse_args(argv)

    if args.self_check:
        return self_check()

    try:
        relative_path, report = analyze_file(args.path, args.git_ref)
    except (OSError, subprocess.CalledProcessError, ValueError) as exc:
        print(str(exc), file=sys.stderr)
        return 2

    print(render_report(relative_path, args.git_ref, report))
    return 0 if report.normalized_equal else 1


if __name__ == "__main__":
    raise SystemExit(main())
