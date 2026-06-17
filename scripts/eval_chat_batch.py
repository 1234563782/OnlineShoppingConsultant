#!/usr/bin/env python3
from __future__ import annotations

import argparse
import http.cookiejar
import json
import re
import sys
import time
import uuid
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any
from urllib import error, parse, request


DEFAULT_BASE_URL = "http://localhost:8087"
DEFAULT_USERNAME = "1234567890"
DEFAULT_PASSWORD = "1234567890"
DEFAULT_DISPLAY_NAME = "12"
DEFAULT_CASES_FILE = Path("evals/chat_cases.jsonl")
DEFAULT_OUTPUT_FILE = Path("evals/chat_results.jsonl")
DEFAULT_SUMMARY_FILE = Path("evals/chat_summary.json")


@dataclass
class EvalCase:
    id: str
    message: str
    expected_intent_type: str
    expected_outcome: str
    expected_category_id: str | None
    expects_tool_use: bool
    notes: str = ""


@dataclass
class EvalRecord:
    id: str
    message: str
    notes: str
    expected: dict[str, Any]
    actual: dict[str, Any]
    metrics: dict[str, Any]
    reply: str
    debug: dict[str, Any]
    events: list[dict[str, Any]]
    elapsed_ms: int


def main() -> int:
    parser = argparse.ArgumentParser(description="Run batch chat evaluation against /api/v1/chat.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--username", default=DEFAULT_USERNAME)
    parser.add_argument("--password", default=DEFAULT_PASSWORD)
    parser.add_argument("--display-name", default=DEFAULT_DISPLAY_NAME)
    parser.add_argument("--cases", default=str(DEFAULT_CASES_FILE))
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT_FILE))
    parser.add_argument("--summary", default=str(DEFAULT_SUMMARY_FILE))
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--register", action="store_true", help="Register the eval user if login fails.")
    parser.add_argument("--shared-session", action="store_true", help="Reuse one sessionId across all cases.")
    parser.add_argument("--quiet", action="store_true", help="Reduce console output.")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    cases_path = resolve_path(repo_root, args.cases)
    output_path = resolve_path(repo_root, args.output)
    summary_path = resolve_path(repo_root, args.summary)

    cases = load_cases(cases_path)
    if args.limit and args.limit > 0:
        cases = cases[: args.limit]
    if not cases:
        print(f"No cases loaded from {cases_path}", file=sys.stderr)
        return 1

    catalog_index = load_catalog_index(repo_root / "scripts" / "init-mysql.sql")
    cookie_jar = http.cookiejar.CookieJar()
    opener = request.build_opener(request.HTTPCookieProcessor(cookie_jar))

    try:
        ensure_auth(
            opener,
            args.base_url,
            args.username,
            args.password,
            args.display_name,
            args.register,
        )
    except Exception as exc:
        print(f"Auth failed: {exc}", file=sys.stderr)
        return 2

    output_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.parent.mkdir(parents=True, exist_ok=True)

    session_id = str(uuid.uuid4())
    records: list[EvalRecord] = []
    started_at = int(time.time() * 1000)

    for index, case in enumerate(cases, start=1):
        current_session_id = session_id if args.shared_session else str(uuid.uuid4())
        if not args.quiet:
            print(f"[{index}/{len(cases)}] {case.id} ...", flush=True)

        try:
            record = run_case(
                opener=opener,
                base_url=args.base_url,
                case=case,
                session_id=current_session_id,
                timeout_seconds=args.timeout,
                catalog_index=catalog_index,
            )
            records.append(record)
            if not args.quiet:
                print(
                    format_case_line(record),
                    flush=True,
                )
        except Exception as exc:
            record = EvalRecord(
                id=case.id,
                message=case.message,
                notes=case.notes,
                expected=expected_payload(case),
                actual={"error": str(exc)},
                metrics={"ok": False},
                reply="",
                debug={},
                events=[],
                elapsed_ms=0,
            )
            records.append(record)
            print(f"{case.id}: ERROR {exc}", file=sys.stderr)

    write_jsonl(output_path, records)
    summary = build_summary(records, cases_path, args.base_url, started_at)
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    print("")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"\nWrote detail report: {output_path}")
    print(f"Wrote summary report: {summary_path}")
    return 0


def resolve_path(repo_root: Path, raw: str) -> Path:
    path = Path(raw)
    return path if path.is_absolute() else repo_root / path


def load_cases(path: Path) -> list[EvalCase]:
    cases: list[EvalCase] = []
    with path.open("r", encoding="utf-8") as fh:
        for line in fh:
            raw = line.strip()
            if not raw or raw.startswith("#"):
                continue
            data = json.loads(raw)
            cases.append(
                EvalCase(
                    id=data["id"],
                    message=data["message"],
                    expected_intent_type=data["expected_intent_type"],
                    expected_outcome=data["expected_outcome"],
                    expected_category_id=data.get("expected_category_id"),
                    expects_tool_use=bool(data.get("expects_tool_use", False)),
                    notes=data.get("notes", ""),
                )
            )
    return cases


def load_catalog_index(sql_path: Path) -> dict[str, dict[str, Any]]:
    if not sql_path.exists():
        return {}

    text = sql_path.read_text(encoding="utf-8", errors="ignore")
    row_pattern = re.compile(
        r"\('(?P<sku>SKU\d+)',\s*'(?P<category>[^']*)',\s*'(?P<category_id>[^']*)',\s*'(?P<category_name>[^']*)',\s*'(?P<name>[^']*)',\s*'(?P<brand>[^']*)',\s*(?P<price>\d+),\s*'(?P<description>[^']*)'\)"
    )

    catalog: dict[str, dict[str, Any]] = {}
    for match in row_pattern.finditer(text):
        sku = match.group("sku")
        catalog[sku.upper()] = {
            "skuId": sku,
            "name": match.group("name"),
            "brand": match.group("brand"),
            "categoryId": match.group("category_id"),
            "categoryName": match.group("category_name"),
            "price": int(match.group("price")),
            "searchText": normalize_text(
                " ".join(
                    [
                        sku,
                        match.group("name"),
                        match.group("brand"),
                        match.group("category_name"),
                        match.group("description"),
                    ]
                )
            ),
            "nameNormalized": normalize_text(match.group("name")),
        }
    return catalog


def ensure_auth(
    opener: request.OpenerDirector,
    base_url: str,
    username: str,
    password: str,
    display_name: str,
    register_first: bool,
) -> None:
    try:
        me = http_get_json(opener, f"{base_url}/api/v1/auth/me")
        if me:
            return
    except Exception:
        pass

    login_payload = {"username": username, "password": password}
    register_error: Exception | None = None
    if register_first:
        try:
            http_post_json(
                opener,
                f"{base_url}/api/v1/auth/register",
                {
                    "username": username,
                    "password": password,
                    "displayName": display_name,
                },
            )
            return
        except Exception:
            register_error = sys.exc_info()[1]

    try:
        http_post_json(opener, f"{base_url}/api/v1/auth/login", login_payload)
    except Exception as exc:
        register_hint = f" Registration error: {register_error}." if register_error else ""
        raise RuntimeError(
            "Unable to authenticate. The app is up, but the eval account is probably missing or the password is different. "
            f"Details: {exc}.{register_hint}"
        ) from exc


def run_case(
    opener: request.OpenerDirector,
    base_url: str,
    case: EvalCase,
    session_id: str,
    timeout_seconds: int,
    catalog_index: dict[str, dict[str, Any]],
) -> EvalRecord:
    started = time.perf_counter()
    events, done_event, reply = post_chat_stream(
        opener=opener,
        base_url=base_url,
        session_id=session_id,
        message=case.message,
        timeout_seconds=timeout_seconds,
    )
    elapsed_ms = int((time.perf_counter() - started) * 1000)

    debug = done_event.get("debug") if isinstance(done_event, dict) else {}
    actual_intent_type = nested_get(debug, ["stateDebug", "extractedPatch", "intentType"])
    actual_turn_outcome = debug.get("turnOutcome") if isinstance(debug, dict) else None
    actual_category_id = resolve_actual_category_id(debug)
    actual_tool_mode = debug.get("toolMode") if isinstance(debug, dict) else None

    authorized_products = extract_authorized_products(debug, catalog_index)
    grounding = evaluate_grounding(reply, authorized_products, catalog_index)

    intent_match = same_text(case.expected_intent_type, actual_intent_type)
    outcome_match = same_text(case.expected_outcome, actual_turn_outcome)
    category_match = True
    if case.expected_category_id is not None:
        category_match = same_text(case.expected_category_id, actual_category_id)

    tool_applicable = case.expects_tool_use
    tool_hit = bool(grounding["grounded"]) if tool_applicable else False
    hallucinated = bool(grounding["unauthorized_mentions"] or grounding["price_mismatches"]) if tool_applicable else False

    metrics = {
        "intent_match": intent_match,
        "outcome_match": outcome_match,
        "category_match": category_match,
        "tool_applicable": tool_applicable,
        "tool_hit": tool_hit,
        "hallucinated": hallucinated,
        "grounded_product_count": len(grounding["grounded_products"]),
        "unauthorized_mentions": grounding["unauthorized_mentions"],
        "price_mismatches": grounding["price_mismatches"],
        "tool_mode": actual_tool_mode,
    }

    actual = {
        "sessionId": session_id,
        "intentType": actual_intent_type,
        "turnOutcome": actual_turn_outcome,
        "categoryId": actual_category_id,
        "toolMode": actual_tool_mode,
        "replyLength": len(reply),
    }

    return EvalRecord(
        id=case.id,
        message=case.message,
        notes=case.notes,
        expected=expected_payload(case),
        actual=actual,
        metrics=metrics,
        reply=reply,
        debug=debug if isinstance(debug, dict) else {},
        events=events,
        elapsed_ms=elapsed_ms,
    )


def post_chat_stream(
    opener: request.OpenerDirector,
    base_url: str,
    session_id: str,
    message: str,
    timeout_seconds: int,
) -> tuple[list[dict[str, Any]], dict[str, Any], str]:
    payload = json.dumps({"sessionId": session_id, "message": message}, ensure_ascii=False).encode("utf-8")
    req = request.Request(
        f"{base_url}/api/v1/chat",
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        },
    )
    req.add_header("Accept", "text/event-stream")

    events: list[dict[str, Any]] = []
    done_event: dict[str, Any] = {}
    reply_parts: list[str] = []
    block_lines: list[str] = []
    start = time.perf_counter()

    with opener.open(req, timeout=timeout_seconds) as resp:
        while True:
            if timeout_seconds and (time.perf_counter() - start) > timeout_seconds:
                raise TimeoutError(f"Timed out after {timeout_seconds}s")

            line = resp.readline()
            if not line:
                if block_lines:
                    event = parse_sse_block("\n".join(block_lines))
                    if event is not None:
                        events.append(event)
                        if event.get("type") == "delta" and event.get("content"):
                            reply_parts.append(str(event["content"]))
                        if event.get("type") == "done":
                            done_event = event
                            if event.get("reply"):
                                return events, done_event, str(event["reply"])
                            return events, done_event, "".join(reply_parts).strip()
                break

            decoded = line.decode("utf-8", errors="replace").rstrip("\r\n")
            if not decoded:
                event = parse_sse_block("\n".join(block_lines))
                block_lines = []
                if event is None:
                    continue
                events.append(event)
                if event.get("type") == "delta" and event.get("content"):
                    reply_parts.append(str(event["content"]))
                if event.get("type") == "done":
                    done_event = event
                    if event.get("reply"):
                        return events, done_event, str(event["reply"])
                    return events, done_event, "".join(reply_parts).strip()
                continue

            block_lines.append(decoded)

    return events, done_event, "".join(reply_parts).strip()


def parse_sse_block(block: str) -> dict[str, Any] | None:
    data_lines = []
    for line in block.splitlines():
        if line.startswith("data:"):
            data_lines.append(line[5:].lstrip())
    if not data_lines:
        return None
    raw = "\n".join(data_lines).strip()
    if not raw:
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"type": "raw", "raw": raw}


def http_get_json(opener: request.OpenerDirector, url: str) -> Any:
    req = request.Request(url, method="GET", headers={"Accept": "application/json"})
    try:
        with opener.open(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"GET {url} failed with HTTP {exc.code}: {body}") from exc


def http_post_json(opener: request.OpenerDirector, url: str, payload: dict[str, Any]) -> Any:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = request.Request(
        url,
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    try:
        with opener.open(req, timeout=30) as resp:
            text = resp.read().decode("utf-8")
            if not text.strip():
                return {}
            return json.loads(text)
    except error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"POST {url} failed with HTTP {exc.code}: {body}") from exc


def extract_authorized_products(
    debug: dict[str, Any],
    catalog_index: dict[str, dict[str, Any]],
) -> list[dict[str, Any]]:
    if not isinstance(debug, dict):
        return []

    products: list[dict[str, Any]] = []
    seen_skus: set[str] = set()

    def append_catalog_sku(raw_sku: Any) -> None:
        sku = str(raw_sku or "").upper()
        if not sku or sku in seen_skus:
            return
        seen_skus.add(sku)
        catalog_product = catalog_index.get(sku)
        if catalog_product is not None:
            products.append(catalog_product)
            return
        products.append({"skuId": sku})

    def append_products(raw_products: Any) -> None:
        if not isinstance(raw_products, list):
            return
        for item in raw_products:
            if not isinstance(item, dict):
                continue
            sku = str(item.get("skuId") or "").upper()
            if sku:
                append_catalog_sku(sku)

    def append_skus(raw_skus: Any) -> None:
        if not isinstance(raw_skus, list):
            return
        for sku in raw_skus:
            append_catalog_sku(sku)

    prefetched_search = debug.get("prefetchedSearch")
    if isinstance(prefetched_search, dict):
        append_skus(prefetched_search.get("skuIds"))
        append_products(prefetched_search.get("products"))

    prefetched_compare = debug.get("prefetchedCompare")
    if isinstance(prefetched_compare, dict):
        append_skus(prefetched_compare.get("skuIds"))
        append_products(prefetched_compare.get("products"))

    session_context = debug.get("sessionContext")
    if isinstance(session_context, dict):
        append_products(session_context.get("lastRecommendations"))

    return products


def resolve_actual_category_id(debug: dict[str, Any]) -> str | None:
    category_resolution = debug.get("categoryResolution") if isinstance(debug, dict) else None
    if isinstance(category_resolution, dict):
        value = category_resolution.get("categoryId")
        if value:
            return str(value)
    effective_context = debug.get("effectiveContext") if isinstance(debug, dict) else None
    if isinstance(effective_context, dict):
        resolved = effective_context.get("resolvedConstraints")
        if isinstance(resolved, dict):
            value = resolved.get("categoryId")
            if value:
                return str(value)
    session_context = debug.get("sessionContext") if isinstance(debug, dict) else None
    if isinstance(session_context, dict):
        value = session_context.get("categoryId")
        if value:
            return str(value)
    return None


def nested_get(value: Any, path: list[str]) -> Any:
    current = value
    for key in path:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def evaluate_grounding(
    reply: str,
    authorized_products: list[dict[str, Any]],
    catalog_index: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    normalized_reply = normalize_text(reply)
    mentioned_authorized: list[dict[str, Any]] = []
    allowed_skus = set()
    for product in authorized_products:
        sku = str(product.get("skuId") or "").upper()
        if sku:
            allowed_skus.add(sku)
        name = str(product.get("name") or "")
        if product_mentioned(reply, name) or sku_mentioned(reply, sku):
            mentioned_authorized.append(product)

    unauthorized_mentions: list[str] = []
    price_mismatches: list[str] = []

    for sku, product in catalog_index.items():
        if sku in allowed_skus:
            continue
        if sku_mentioned(reply, sku):
            unauthorized_mentions.append(product["skuId"])
            continue
        if product_mentioned(reply, product["name"]):
            unauthorized_mentions.append(product["skuId"])

    for product in mentioned_authorized:
        expected_price = int(product.get("price") or 0)
        if expected_price <= 0:
            continue
        if not mentions_expected_price(normalized_reply, product, expected_price):
            price_mismatches.append(str(product.get("skuId")))

    return {
        "grounded_products": mentioned_authorized,
        "unauthorized_mentions": unauthorized_mentions,
        "price_mismatches": price_mismatches,
        "grounded": bool(mentioned_authorized) and not unauthorized_mentions and not price_mismatches,
    }


def product_mentioned(reply: str, product_name: Any) -> bool:
    name = str(product_name or "").strip()
    if not name:
        return False

    pattern = product_name_pattern(name)
    if pattern is None:
        return False
    return bool(pattern.search(reply or ""))


def sku_mentioned(reply: str, sku_id: Any) -> bool:
    sku = str(sku_id or "").strip().upper()
    if not sku:
        return False
    pattern = re.compile(rf"(?<![A-Za-z0-9]){re.escape(sku)}(?![A-Za-z0-9])", re.IGNORECASE)
    return bool(pattern.search(reply or ""))


def product_name_pattern(product_name: str) -> re.Pattern[str] | None:
    chunks = re.findall(r"[A-Za-z0-9]+|[\u4e00-\u9fff]+", product_name)
    if not chunks:
        return None

    parts: list[str] = []
    for index, chunk in enumerate(chunks):
        if index > 0:
            parts.append(r"[\s\W_]*")
        if re.fullmatch(r"[A-Za-z0-9]+", chunk):
            parts.append(rf"(?<![A-Za-z0-9]){re.escape(chunk)}(?![A-Za-z0-9])")
        else:
            parts.append(re.escape(chunk))

    return re.compile("".join(parts), re.IGNORECASE)


def mentions_expected_price(reply: str, product: dict[str, Any], expected_price: int) -> bool:
    expected_price_text = str(expected_price)
    tokens: list[str] = []

    sku = normalize_text(product.get("skuId"))
    if sku:
        tokens.append(sku)

    name = normalize_text(product.get("name"))
    if name and name not in tokens:
        tokens.append(name)

    for token in tokens:
        start = 0
        while True:
            idx = reply.find(token, start)
            if idx < 0:
                break
            window_start = max(0, idx - 60)
            window_end = min(len(reply), idx + len(token) + 120)
            window = reply[window_start:window_end]
            if expected_price_text in window:
                return True
            start = idx + len(token)

    return False


def normalize_text(text: Any) -> str:
    if text is None:
        return ""
    return re.sub(r"[^\u4e00-\u9fffA-Za-z0-9]+", "", str(text)).lower()


def same_text(expected: Any, actual: Any) -> bool:
    if expected is None:
        return actual is None or str(actual).strip() == ""
    return normalize_text(expected) == normalize_text(actual)


def expected_payload(case: EvalCase) -> dict[str, Any]:
    return {
        "intentType": case.expected_intent_type,
        "turnOutcome": case.expected_outcome,
        "categoryId": case.expected_category_id,
        "expectsToolUse": case.expects_tool_use,
    }


def format_case_line(record: EvalRecord) -> str:
    metrics = record.metrics
    return (
        f"{record.id}: "
        f"intent={flag(metrics['intent_match'])} "
        f"outcome={flag(metrics['outcome_match'])} "
        f"category={flag(metrics['category_match'])} "
        f"tool={flag(metrics['tool_hit']) if metrics['tool_applicable'] else '-'} "
        f"hallucination={'Y' if metrics['hallucinated'] else 'N'} "
        f"reply={truncate(record.reply, 72)}"
    )


def flag(value: Any) -> str:
    return "Y" if bool(value) else "N"


def truncate(text: str, width: int) -> str:
    clean = text.strip().replace("\n", " ")
    if len(clean) <= width:
        return clean
    return clean[: max(0, width - 3)] + "..."


def write_jsonl(path: Path, records: list[EvalRecord]) -> None:
    with path.open("w", encoding="utf-8") as fh:
        for record in records:
            fh.write(json.dumps(asdict(record), ensure_ascii=False) + "\n")


def build_summary(
    records: list[EvalRecord],
    cases_path: Path,
    base_url: str,
    started_at_ms: int,
) -> dict[str, Any]:
    total = len(records)
    intent_total = sum(1 for r in records if r.expected.get("intentType") is not None)
    outcome_total = sum(1 for r in records if r.expected.get("turnOutcome") is not None)
    category_total = sum(1 for r in records if r.expected.get("categoryId") is not None)
    tool_total = sum(1 for r in records if r.metrics.get("tool_applicable"))

    def rate(passed: int, denominator: int) -> float:
        return 0.0 if denominator == 0 else passed / denominator

    intent_passed = sum(1 for r in records if r.metrics.get("intent_match"))
    outcome_passed = sum(1 for r in records if r.metrics.get("outcome_match"))
    category_passed = sum(
        1
        for r in records
        if r.expected.get("categoryId") is not None and r.metrics.get("category_match")
    )
    tool_hit_passed = sum(1 for r in records if r.metrics.get("tool_applicable") and r.metrics.get("tool_hit"))
    hallucinated = sum(1 for r in records if r.metrics.get("tool_applicable") and r.metrics.get("hallucinated"))

    return {
        "runId": str(uuid.uuid4()),
        "baseUrl": base_url,
        "casesFile": str(cases_path),
        "startedAtMs": started_at_ms,
        "finishedAtMs": int(time.time() * 1000),
        "totalCases": total,
        "intentAccuracy": {
            "passed": intent_passed,
            "total": intent_total,
            "rate": rate(intent_passed, intent_total),
        },
        "turnOutcomeAccuracy": {
            "passed": outcome_passed,
            "total": outcome_total,
            "rate": rate(outcome_passed, outcome_total),
        },
        "categoryAccuracy": {
            "passed": category_passed,
            "total": category_total,
            "rate": rate(category_passed, category_total),
        },
        "toolHitRate": {
            "passed": tool_hit_passed,
            "total": tool_total,
            "rate": rate(tool_hit_passed, tool_total),
        },
        "hallucinationRate": {
            "passed": hallucinated,
            "total": tool_total,
            "rate": rate(hallucinated, tool_total),
        },
    }


if __name__ == "__main__":
    raise SystemExit(main())
