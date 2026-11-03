from niceexcel_api.conditional_formatting import duplicate_counts, normalize_rule, rule_matches
import pytest


def test_numeric_comparison_and_between_rules():
    rule = normalize_rule({"type": "cellValue", "range": "A1:A5", "operator": "greaterThan", "value": 10})
    assert rule_matches(rule, 11)
    assert not rule_matches(rule, 10)
    between = normalize_rule({"type": "cellValue", "range": "A1:A5", "operator": "between", "value": 5, "value2": 9})
    assert rule_matches(between, 7)
    assert not rule_matches(between, 10)


def test_text_blank_and_duplicate_rules():
    text = normalize_rule({"type": "textContains", "range": "B1:B4", "text": "urgent"})
    assert rule_matches(text, "URGENT: review")
    blank = normalize_rule({"type": "blanks", "range": "B1:B4"})
    assert rule_matches(blank, "  ")
    duplicates = normalize_rule({"type": "duplicateValues", "range": "A1:A4"})
    counts = duplicate_counts(["Apple", "apple", "Pear", None])
    assert rule_matches(duplicates, "Apple", counts["apple"])
    assert not rule_matches(duplicates, "Pear", counts["pear"])


def test_invalid_rule_contract_is_rejected():
    with pytest.raises(ValueError):
        normalize_rule({"type": "unknown", "range": "A1:A2"})
    with pytest.raises(ValueError):
        normalize_rule({"type": "cellValue", "range": "A1", "operator": "greaterThan", "value": 2})
    with pytest.raises(ValueError):
        normalize_rule({"type": "textContains", "range": "A1:A2", "text": ""})


def test_rule_style_colours_are_normalized():
    rule = normalize_rule({"type": "blanks", "range": "A1:A2", "style": {"fillColor": "ffcc00"}})
    assert rule["style"]["fillColor"] == "#FFCC00"
