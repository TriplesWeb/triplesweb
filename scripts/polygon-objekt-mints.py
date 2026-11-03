#!/usr/bin/env python3
"""Collect Polygon-era Objekt mint logs only (ERC-721 Transfer from 0x0).

No Cosmo metadata, no collaborator MariaDB, no Flyway. Writes local JSONL
plus a resume checkpoint. Public RPCs serve this history in 10_000-block
windows; 25_000-block windows return HTTP 413.
"""

from __future__ import annotations

import argparse
import json
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

TRIPLES = "0xA4B37bE40F7b231Ee9574c4b16b7DDb7EAcDC99B"
ARTMS = "0x0fB69F54bA90f17578a59823E09e5a1f8F3FA200"
TRANSFER = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
ZERO = "0x" + "0" * 64
CONTRACTS = {
    "triples": {"address": TRIPLES, "start": 29_388_703},
    "artms": {"address": ARTMS, "start": 44_048_396},
}
# Last observed Polygon mint sits at 70361022 (tripleS) / 70360862 (ARTMS).
DEFAULT_TO = 70_370_000
DEFAULT_RPCS = [
    "https://polygon.gateway.tenderly.co",
    "https://rpc-mainnet.matic.quiknode.pro",
]
MAX_WINDOW = 10_000


def topic_address(topic: str) -> str:
    return "0x" + topic[-40:].lower()


def decode_mint(log: dict[str, Any], artist: str) -> dict[str, Any]:
    topics = log.get("topics") or []
    if len(topics) < 4:
        raise ValueError("Transfer log needs 4 topics")
    if topics[1].lower() != ZERO:
        raise ValueError("not a mint")
    return {
        "artist": artist,
        "block": int(log["blockNumber"], 16),
        "tokenId": str(int(topics[3], 16)),
        "to": topic_address(topics[2]),
        "tx": log["transactionHash"],
        "contract": log["address"].lower(),
    }


def rpc_call(urls: list[str], method: str, params: list[Any], timeout: float = 45.0) -> Any:
    last_error: Exception | None = None
    body = json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params}).encode()
    for url in urls:
        req = urllib.request.Request(url, data=body, headers={"content-type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as response:
                payload = json.loads(response.read())
            if "error" in payload:
                last_error = RuntimeError(str(payload["error"]))
                continue
            return payload["result"]
        except urllib.error.HTTPError as error:
            last_error = error
            if error.code in {413, 400} and len(params) == 1 and isinstance(params[0], dict):
                raise
            continue
        except Exception as error:  # noqa: BLE001 — failover across public RPCs
            last_error = error
            continue
    raise RuntimeError(f"all RPCs failed for {method}: {last_error}")


def fetch_mints(urls: list[str], address: str, start: int, end: int) -> list[dict[str, Any]]:
    params = {
        "address": address,
        "fromBlock": hex(start),
        "toBlock": hex(end),
        "topics": [TRANSFER, ZERO],
    }
    try:
        return rpc_call(urls, "eth_getLogs", [params])
    except urllib.error.HTTPError as error:
        if error.code not in {413, 400} or end <= start:
            raise
        mid = (start + end) // 2
        return fetch_mints(urls, address, start, mid) + fetch_mints(urls, address, mid + 1, end)


def windows_for(artist: str, start: int, end: int, size: int) -> list[tuple[str, int, int]]:
    contract_start = CONTRACTS[artist]["start"]
    cursor = max(start, contract_start)
    out: list[tuple[str, int, int]] = []
    while cursor <= end:
        chunk_end = min(end, cursor + size - 1)
        out.append((artist, cursor, chunk_end))
        cursor = chunk_end + 1
    return out


def load_checkpoint(path: Path) -> set[str]:
    if not path.exists():
        return set()
    data = json.loads(path.read_text())
    return set(data.get("completed") or [])


def window_key(artist: str, start: int, end: int) -> str:
    return f"{artist}:{start}-{end}"


def self_test() -> None:
    token = 9203435
    padded = hex(token)[2:].rjust(64, "0")
    log = {
        "blockNumber": hex(70_000_004),
        "transactionHash": "0xabc",
        "address": TRIPLES,
        "topics": [TRANSFER, ZERO, "0x" + "0" * 24 + "1" * 40, "0x" + padded],
    }
    event = decode_mint(log, "triples")
    assert event["tokenId"] == "9203435"
    assert event["block"] == 70_000_004
    wins = windows_for("triples", 70_000_000, 70_000_100, 50)
    assert wins[0] == ("triples", 70_000_000, 70_000_049)
    print("SELF_TEST_OK")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rpc", action="append", default=[], help="RPC URL; repeatable, tried in order")
    parser.add_argument("--from-block", type=int, default=29_388_703)
    parser.add_argument("--to-block", type=int, default=DEFAULT_TO)
    parser.add_argument("--window", type=int, default=MAX_WINDOW)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--out", default="/tmp/polygon-mints/mints.jsonl")
    parser.add_argument("--checkpoint", default="/tmp/polygon-mints/checkpoint.json")
    parser.add_argument("--status", default="/tmp/polygon-mints/status.json")
    parser.add_argument("--self-test", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if args.self_test:
        self_test()
        return 0

    urls = args.rpc or list(DEFAULT_RPCS)
    start, end = args.from_block, args.to_block
    if end < start:
        print("to-block must be >= from-block", file=sys.stderr)
        return 2
    window = max(1, min(args.window, MAX_WINDOW))
    tasks = windows_for("triples", start, end, window) + windows_for("artms", start, end, window)

    out_path = Path(args.out)
    ck_path = Path(args.checkpoint)
    status_path = Path(args.status)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    done = load_checkpoint(ck_path)
    pending = [task for task in tasks if window_key(*task) not in done]
    print(
        f"mint-scan {start}-{end} window={window} workers={args.workers} "
        f"tasks={len(tasks)} pending={len(pending)}",
        file=sys.stderr,
    )

    lock = threading.Lock()
    mints = 0
    finished = 0
    t0 = time.time()
    if ck_path.exists():
        prev = json.loads(ck_path.read_text())
        mints = int(prev.get("mints") or 0)

    def write_status(extra: dict[str, Any] | None = None) -> None:
        elapsed = max(time.time() - t0, 0.001)
        payload = {
            "from": start,
            "to": end,
            "pending": len(pending) - finished,
            "completedWindows": len(done),
            "totalWindows": len(tasks),
            "mints": mints,
            "elapsedSec": round(elapsed, 1),
            "windowsPerSec": round(finished / elapsed, 3),
            **(extra or {}),
        }
        status_path.write_text(json.dumps(payload))

    def run_window(task: tuple[str, int, int]) -> tuple[str, int, int, list[dict[str, Any]]]:
        artist, lo, hi = task
        logs = fetch_mints(urls, CONTRACTS[artist]["address"], lo, hi)
        events = [decode_mint(item, artist) for item in logs]
        return artist, lo, hi, events

    with out_path.open("a", encoding="utf-8") as handle:
        workers = max(1, args.workers)
        with ThreadPoolExecutor(max_workers=workers) as pool:
            futures = [pool.submit(run_window, task) for task in pending]
            for future in as_completed(futures):
                artist, lo, hi, events = future.result()
                key = window_key(artist, lo, hi)
                with lock:
                    for event in events:
                        handle.write(json.dumps(event, separators=(",", ":")) + "\n")
                    handle.flush()
                    done.add(key)
                    mints += len(events)
                    finished += 1
                    ck_path.write_text(
                        json.dumps({"completed": sorted(done), "mints": mints})
                    )
                    if finished % 20 == 0 or finished == len(pending):
                        write_status({"last": key, "lastMints": len(events)})
                        elapsed = max(time.time() - t0, 0.001)
                        eta = (len(pending) - finished) / max(finished / elapsed, 0.001)
                        print(
                            f"{finished}/{len(pending)} mints={mints} last={key} "
                            f"+{len(events)} eta={eta:.0f}s",
                            file=sys.stderr,
                            flush=True,
                        )

    write_status({"done": True})
    print(json.dumps({"mints": mints, "windows": len(done), "out": str(out_path)}), file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
