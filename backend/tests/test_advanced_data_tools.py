from niceexcel_api.main import app, _workbooks

client = app.test_client()


def test_data_validation_is_saved_and_rejects_invalid_cell_values():
    book_id = client.post("/workbooks", json={}).get_json()["workbook_id"]
    saved = client.put(f"/workbooks/{book_id}/data-validation", json={
        "sheet": "Sheet1",
        "rules": [{"range": "A2:A5", "type": "list", "values": ["Open", "Closed"]}]
    })
    assert saved.status_code == 200, saved.get_data(as_text=True)
    assert client.patch(f"/workbooks/{book_id}/cells", json={
        "sheet": "Sheet1", "cells": [{"cell": "A2", "value": "Open"}]
    }).status_code == 200
    invalid = client.patch(f"/workbooks/{book_id}/cells", json={
        "sheet": "Sheet1", "cells": [{"cell": "A3", "value": "Pending"}]
    })
    assert invalid.status_code == 422
    assert "Choose one of" in invalid.get_json()["detail"]
    _workbooks.pop(book_id, None)
    loaded = client.get(f"/workbooks/{book_id}/data-validation", query_string={"sheet": "Sheet1"})
    assert loaded.status_code == 200
    assert loaded.get_json()["rules"][0]["values"] == ["Open", "Closed"]
    assert "_NiceExcelDV" not in [item["name"] for item in client.get(f"/workbooks/{book_id}").get_json()["sheets"]]


def test_data_validation_numeric_limits_and_invalid_rules():
    book_id = client.post("/workbooks", json={}).get_json()["workbook_id"]
    saved = client.put(f"/workbooks/{book_id}/data-validation", json={
        "sheet": "Sheet1",
        "rules": [{"range": "B1:B4", "type": "whole", "operator": "between", "value": 1, "value2": 5}]
    })
    assert saved.status_code == 200
    assert client.patch(f"/workbooks/{book_id}/cells", json={
        "sheet": "Sheet1", "cells": [{"cell": "B1", "value": 3}]
    }).status_code == 200
    assert client.patch(f"/workbooks/{book_id}/cells", json={
        "sheet": "Sheet1", "cells": [{"cell": "B2", "value": 6}]
    }).status_code == 422
    bad = client.put(f"/workbooks/{book_id}/data-validation", json={
        "sheet": "Sheet1", "rules": [{"range": "A1", "type": "list", "values": []}]
    })
    assert bad.status_code == 422
