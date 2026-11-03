"""Shared conditional-format rule contract and evaluator for NiceExcel clients.

Rule JSON is intentionally language-neutral so Web and Native clients can exchange it.
Supported v1 types: cellValue, textContains, duplicateValues, blanks.
"""
from __future__ import annotations

from collections import Counter
from typing import Any

RULE_TYPES = {"cellValue", "textContains", "duplicateValues", "blanks"}
OPERATORS = {"greaterThan", "greaterThanOrEqual", "lessThan", "lessThanOrEqual", "equal", "notEqual", "between", "notBetween"}


def normalize_rule(rule: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(rule, dict):
        raise ValueError("Each conditional-format rule must be an object")
    rule_type = str(rule.get("type", ""))
    if rule_type not in RULE_TYPES:
        raise ValueError(f"Unsupported conditional-format rule type: {rule_type}")
    cell_range = str(rule.get("range", "")).strip().upper()
    import re
    if not re.fullmatch(r"[A-Z]{1,3}[1-9][0-9]*:[A-Z]{1,3}[1-9][0-9]*", cell_range):
        raise ValueError("Rule range must be a bounded range such as A1:D20")
    result = {
        "id": str(rule.get("id") or ""),
        "type": rule_type,
        "range": cell_range,
        "operator": str(rule.get("operator", "greaterThan")),
        "value": rule.get("value"),
        "value2": rule.get("value2"),
        "text": str(rule.get("text", "")),
        "style": {},
        "stopIfTrue": bool(rule.get("stopIfTrue", False)),
    }
    style = rule.get("style") or {}
    if not isinstance(style, dict):
        raise ValueError("Rule style must be an object")
    for key in ("fillColor", "fontColor"):
        color = style.get(key)
        if color is not None:
            color = str(color).removeprefix("#").upper()
            if not re.fullmatch(r"[0-9A-F]{6}", color):
                raise ValueError(f"{key} must be a six-digit hex colour")
            result["style"][key] = "#" + color
    if rule_type == "cellValue" and result["operator"] not in OPERATORS:
        raise ValueError(f"Unsupported comparison operator: {result['operator']}")
    if rule_type == "textContains" and not result["text"]:
        raise ValueError("Text rule requires a non-empty text value")
    return result


def _number(value: Any) -> float | None:
    if isinstance(value, bool) or value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def rule_matches(rule: dict[str, Any], value: Any, duplicate_count: int = 0) -> bool:
    """Evaluate one normalized rule against a cell value."""
    kind = rule["type"]
    if kind == "blanks":
        return value is None or str(value).strip() == ""
    if kind == "duplicateValues":
        return duplicate_count > 1
    if kind == "textContains":
        return rule["text"].casefold() in str(value if value is not None else "").casefold()
    left, right = _number(value), _number(rule.get("value"))
    if left is None or right is None:
        return False
    op = rule["operator"]
    if op == "greaterThan": return left > right
    if op == "greaterThanOrEqual": return left >= right
    if op == "lessThan": return left < right
    if op == "lessThanOrEqual": return left <= right
    if op == "equal": return left == right
    if op == "notEqual": return left != right
    if op == "between":
        upper = _number(rule.get("value2"))
        return upper is not None and min(right, upper) <= left <= max(right, upper)
    if op == "notBetween":
        upper = _number(rule.get("value2"))
        return upper is not None and not (min(right, upper) <= left <= max(right, upper))
    return False


def duplicate_counts(values: list[Any]) -> Counter:
    keys = [str(v).strip().casefold() for v in values if v is not None and str(v).strip()]
    return Counter(keys)
