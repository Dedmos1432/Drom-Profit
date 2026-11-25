from pathlib import Path

import pytest
import yaml
from fastapi.openapi.utils import get_openapi
from fastapi.testclient import TestClient

from app.main import create_app
from app.money import MONEY_LIMIT
from app.schemas import Error, MAX_INT64
from app.services.listing_import import ListingImportError


CONTRACT_PATH = Path(__file__).resolve().parents[2] / "shared/openapi.yaml"


def json_schema(operation, section):
    schema = operation.get(section, {}).get("content", {}).get("application/json", {}).get("schema")
    return None if schema is None else field_contract(schema)


def field_contract(field, *, money=False):
    field = dict(field)
    if "anyOf" in field:
        value, null = field["anyOf"]
        assert null == {"type": "null"}
        field = value | {"nullable": True}
    if "const" in field:
        field["enum"] = [field.pop("const")]
    if field.get("format") == "uri" and field.get("minLength") == 1:
        del field["minLength"]
    return {
        key: value
        for key, value in field.items()
        if key in {"$ref", "type", "items", "enum", "nullable", "minimum", "minLength", "minItems",
                   "uniqueItems", "format"} or (money and key == "maximum")
    }


def test_swagger_serves_shared_contract(client):
    contract = yaml.safe_load(CONTRACT_PATH.read_text("utf-8"))
    response = client.get("/openapi.json")
    assert response.status_code == 200 and response.json() == contract
    docs = client.get("/docs")
    assert docs.status_code == 200 and "/openapi.json" in docs.text



def test_runtime_routes_match_shared_contract():
    contract = yaml.safe_load(CONTRACT_PATH.read_text("utf-8"))
    app = create_app()
    actual = get_openapi(title=app.title, version=app.version, routes=app.routes)
    assert actual["paths"].keys() == contract["paths"].keys()
    assert actual["components"]["securitySchemes"] == contract["components"]["securitySchemes"]
    for path, methods in contract["paths"].items():
        assert actual["paths"][path].keys() == methods.keys()
        for method, spec in methods.items():
            route = actual["paths"][path][method]
            assert route.get("security", []) == spec.get("security", contract["security"])
            assert {param["name"] for param in route.get("parameters", [])} == {
                param["name"] for param in spec.get("parameters", [])
            }
            assert json_schema(route, "requestBody") == json_schema(spec, "requestBody")
            success = next(status for status in spec["responses"] if status.startswith("2"))
            assert [status for status in route["responses"] if status.startswith("2")] == [success]
            assert (
                json_schema(route["responses"], success) == json_schema(spec["responses"], success)
            )
    for name, spec in contract["components"]["schemas"].items():
        schema = Error.model_json_schema() if name == "Error" else actual["components"]["schemas"][name]
        assert set(spec.get("required", [])) == set(schema.get("required", [])), name
        assert spec["additionalProperties"] == schema["additionalProperties"], name
        assert spec["properties"].keys() == schema["properties"].keys(), name
        for field, expected in spec["properties"].items():
            money = expected.get("format") == "int64"
            assert field_contract(schema["properties"][field], money=money) == field_contract(
                expected, money=money
            ), (name, field)


@pytest.mark.parametrize("price", [None, 0, -1, 1.5, 2.0, True, "1000", MAX_INT64 + 1])
def test_rejects_invalid_price(client, headers, price):
    response = client.post(
        "/api/cars", headers=headers,
        json={"source": "manual", "brand": "Toyota", "model": "Camry", "price": price},
    )
    assert response.status_code == 422
    code = "amount_out_of_range" if type(price) is int else "validation_error"
    assert response.json()["code"] == code
    assert set(response.json()) == {"code", "message"}
    assert client.get("/api/cars", headers=headers).json() == []


@pytest.mark.parametrize(
    "collection,params",
    [("expenses", {"kind": "repair"}), ("benefits", {})],
)
@pytest.mark.parametrize("amount", [-1, 3.1, 4.0, False, "0", MAX_INT64 + 1])
def test_rejects_invalid_factor_money(client, headers, new_car, collection, params, amount):
    path = new_car() + "/" + collection
    for body in [{"title": "Фактор"} | params, {"title": "Фактор", "amount": amount} | params]:
        assert client.post(path, headers=headers, json=body).status_code == 422


def test_patch_rejects_nonnullable_unknown_and_immutable_fields(client, headers, new_car):
    path = new_car()
    for patch in [
        {"price": None}, {"brand": None}, {"expenses_reviewed": None},
        {"source": "demo"}, {"session_id": "other"}, {"mileage_km": -1},
        {"benefits_reviewed": "true"}, {"listing_id": "changed"},
    ]:
        assert client.patch(path, headers=headers, json=patch).status_code == 422
    for collection, body, field in [
        ("expenses", {"title": "Ремонт", "kind": "repair", "amount": 0}, "kind"),
        ("benefits", {"title": "Резина", "amount": 0}, "title"),
        ("notes", {"kind": "other", "text": "Заметка"}, "text"),
    ]:
        item = client.post(f"{path}/{collection}", headers=headers, json=body).json()
        response = client.patch(
            f"{path}/{collection}/{item['id']}", headers=headers, json={field: None}
        )
        assert response.status_code == 422
    malformed = client.patch(path, headers=headers | {"Content-Type": "application/json"}, content="{")
    assert malformed.status_code == 422
    assert malformed.json()["code"] == "validation_error"


def test_large_money_totals_remain_exact(client, headers, new_car):
    path = new_car(price=MONEY_LIMIT)
    expense = client.post(
        f"{path}/expenses", headers=headers,
        json={"title": "Ремонт", "kind": "repair", "amount": MONEY_LIMIT},
    )
    assert expense.status_code == 201
    result = client.get(f"{path}/summary", headers=headers).json()
    assert result["acquisition_cost"] == result["adjusted_cost"] == 2 * MONEY_LIMIT


@pytest.mark.parametrize("ids", [[], ["a"], ["a", "a"], [1, "a"]])
def test_comparison_requires_two_unique_string_ids(client, headers, ids):
    response = client.post("/api/comparison", headers=headers, json={"car_ids": ids})
    assert response.status_code == 422
    assert response.json()["code"] == "validation_error"


def test_demo_preview_then_create(client, headers):
    url = "https://auto.drom.ru/demo/1.html"
    preview = client.post("/api/cars/import", headers=headers, json={"url": url})
    assert preview.status_code == 200
    listing = preview.json()
    assert listing["source"] == "demo" and listing["listing_url"] == url
    assert listing["image_url"] is None
    assert client.get("/api/cars", headers=headers).json() == []
    saved = client.post("/api/cars", headers=headers, json=listing)
    assert saved.status_code == 201
    assert all(saved.json()[key] == value for key, value in listing.items())


@pytest.mark.parametrize(
    "url,status,code",
    [
        ("https://auto.drom.ru/demo/999.html", 404, "listing_unavailable"),
        ("http://auto.drom.ru/demo/1.html", 400, "invalid_url"),
        ("https://user@auto.drom.ru/demo/1.html", 400, "invalid_url"),
        ("https://auto.drom.ru:443/demo/1.html", 400, "invalid_url"),
        ("https://example.org/demo/1.html", 400, "invalid_url"),
        ("broken", 400, "invalid_url"),
    ],
)
def test_demo_preview_errors(client, headers, url, status, code):
    preview = client.post("/api/cars/import", headers=headers, json={"url": url})
    assert preview.status_code == status
    assert preview.json()["code"] == code


@pytest.mark.parametrize("code,status", [("source_forbidden", 403), ("parse_error", 502), ("source_unavailable", 503)])
def test_importer_can_be_injected(db_url, code, status):
    async def import_listing(_url):
        raise ListingImportError(code)

    with TestClient(create_app(db_url, importer=import_listing)) as client:
        token = client.post("/api/session").json()["token"]
        response = client.post(
            "/api/cars/import",
            headers={"Authorization": f"Bearer {token}"},
            json={"url": "https://auto.drom.ru/demo/1.html"},
        )
        assert response.status_code == status
        assert response.json()["code"] == code
