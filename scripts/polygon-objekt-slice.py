#!/usr/bin/env python3
"""Bounded Polygon Objekt Transfer slice.

Reads ERC-721 Transfer logs for the old Cosmo contracts and writes JSONL.
Does not touch collaborator MariaDB, Flyway, or GitHub CI.

Public archive-capable RPCs (QuickNode Polygon, Tenderly) can serve this
history in <=10_000-block windows. Larger windows often return HTTP 413.
Subsquid's public gateway now requires a portal API key.

Cosmo `objekt/v1/token/{id}` is 503. Optional `--cosmo` uses
`/bff/v3/objekts/nft-metadata/{id}` which has collection traits but not
card serial (`objektNo`).
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from typing import Any

TRIPLES_OBJEKT = "0xA4B37bE40F7b231Ee9574c4b16b7DDb7EAcDC99B"
ARTMS_OBJEKT = "0x0fB69F54bA90f17578a59823E09e5a1f8F3FA200"
TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
ZERO_TOPIC = "0x" + "0" * 64
DEFAULT_RPC = "https://rpc-mainnet.matic.quiknode.pro"
COSMO_V3 = "https://api.cosmo.fans/bff/v3/objekts/nft-metadata/{id}"
ARTIST_START = 29_388_703
MAX_BLOCKS_WITHOUT_OVERRIDE = 20_000
MAX_WINDOW = 10_000


def topic_address(topic: str) -> str:
    return "0x" + topic[-40:].lower()


def decode_transfer(log: dict[str, Any]) -> dict[str, Any]:
    topics = log.get("topics") or []
    if len(topics) < 4:
        raise ValueError("Transfer log needs 4 topics")
    token_id = str(int(topics[3], 16))
    frm = topic_address(topics[1])
    to = topic_address(topics[2])
    return {
        "block": int(log["blockNumber"], 16),
        "tx": log["transactionHash"],
        "contract": log["address"].lower(),
        "from": frm,
        "to": to,
        "tokenId": token_id,
        "mint": topics[1].lower() == ZERO_TOPIC,
    }


def rpc(url: str, method: str, params: list[Any], timeout: float = 40.0) -> Any:
    body = json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params}).encode()
    req = urllib.request.Request(url, data=body, headers={"content-type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            payload = json.loads(response.read())
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"RPC HTTP {error.code} for {method}") from error
    if "error" in payload:
        raise RuntimeError(f"RPC {method} error: {payload['error']}")
    return payload["result"]


def get_logs(url: str, address: str, start: int, end: int, mints_only: bool) -> list[dict[str, Any]]:
    topics: list[Any] = [TRANSFER_TOPIC]
    if mints_only:
        topics.append(ZERO_TOPIC)
    return rpc(
        url,
        "eth_getLogs",
        [
            {
                "address": address,
                "fromBlock": hex(start),
                "toBlock": hex(end),
                "topics": topics,
            }
        ],
    )


def cosmo_metadata(token_id: str, timeout: float = 15.0) -> dict[str, Any] | None:
    req = urllib.request.Request(
        COSMO_V3.format(id=token_id),
        headers={"Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            meta = json.loads(response.read())
    except Exception:
        return None
    traits = {
        item.get("trait_type"): item.get("value")
        for item in meta.get("attributes") or []
        if isinstance(item, dict)
    }
    season = traits.get("Season") or ""
    member = traits.get("Member") or ""
    collection_no = traits.get("Collection") or ""
    return {
        "name": meta.get("name"),
        "season": season,
        "member": member,
        "collectionNo": collection_no,
        "class": traits.get("Class") or "",
        "artist": traits.get("Artist") or "",
        "slug": "-".join(
            part.lower() for part in (season, member, collection_no) if part
        ),
    }


def self_test() -> None:
    token = 9203435
    padded = hex(token)[2:].rjust(64, "0")
    log = {
        "blockNumber": hex(70_000_004),
        "transactionHash": "0xabc",
        "address": TRIPLES_OBJEKT,
        "topics": [TRANSFER_TOPIC, ZERO_TOPIC, "0x" + "0" * 24 + "1" * 40, "0x" + padded],
    }
    event = decode_transfer(log)
    assert event["mint"] is True
    assert event["tokenId"] == "9203435"
    assert event["block"] == 70_000_004
    assert event["from"] == "0x" + "0" * 40
    print("SELF_TEST_OK")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rpc", default=DEFAULT_RPC)
    parser.add_argument("--from-block", type=int, default=70_000_000)
    parser.add_argument("--to-block", type=int, default=70_000_100)
    parser.add_argument("--window", type=int, default=100)
    parser.add_argument("--out", default="-")
    parser.add_argument("--cosmo", action="store_true", help="Attach Cosmo v3 collection traits")
    parser.add_argument("--mints-only", action="store_true")
    parser.add_argument("--artms", action="store_true", help="Also scan the ARTMS Objekt contract")
    parser.add_argument(
        "--i-understand-full-scan",
        action="store_true",
        help="Allow a range larger than 20_000 blocks. Do not use this for a multi-day genesis scan by accident.",
    )
    parser.add_argument("--self-test", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if args.self_test:
        self_test()
        return 0

    start, end = args.from_block, args.to_block
    if end < start:
        print("to-block must be >= from-block", file=sys.stderr)
        return 2
    span = end - start + 1
    if span > MAX_BLOCKS_WITHOUT_OVERRIDE and not args.i_understand_full_scan:
        print(
            f"range {span} blocks exceeds {MAX_BLOCKS_WITHOUT_OVERRIDE}; "
            "pass --i-understand-full-scan to continue",
            file=sys.stderr,
        )
        return 2
    window = max(1, min(args.window, MAX_WINDOW))
    contracts = [TRIPLES_OBJEKT]
    if args.artms:
        contracts.append(ARTMS_OBJEKT)

    out = sys.stdout if args.out == "-" else open(args.out, "w", encoding="utf-8")
    stats = {"logs": 0, "mints": 0, "cosmo": 0, "windows": 0}
    t0 = time.time()
    try:
        for contract in contracts:
            cursor = max(start, ARTIST_START)
            while cursor <= end:
                chunk_end = min(end, cursor + window - 1)
                logs = get_logs(args.rpc, contract, cursor, chunk_end, args.mints_only)
                stats["windows"] += 1
                for log in logs:
                    event = decode_transfer(log)
                    stats["logs"] += 1
                    if event["mint"]:
                        stats["mints"] += 1
                    if args.cosmo:
                        meta = cosmo_metadata(event["tokenId"])
                        if meta:
                            event["cosmo"] = meta
                            stats["cosmo"] += 1
                    out.write(json.dumps(event, ensure_ascii=False) + "\n")
                cursor = chunk_end + 1
    finally:
        if out is not sys.stdout:
            out.close()

    summary = {
        "from": start,
        "to": end,
        "rpc": args.rpc,
        "elapsedSec": round(time.time() - t0, 2),
        **stats,
    }
    print(json.dumps(summary, ensure_ascii=False), file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
