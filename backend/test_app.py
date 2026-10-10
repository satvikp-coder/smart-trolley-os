from fastapi.testclient import TestClient

from app import app

client = TestClient(app)


def test_health():
    assert client.get("/api/health").json()["status"] == "ok"


def test_missing_providers_are_explicitly_unconfigured(monkeypatch):
    monkeypatch.delenv("GRAPHHOPPER_API_KEY", raising=False)
    monkeypatch.delenv("AVIATIONSTACK_ACCESS_KEY", raising=False)
    assert client.get("/api/navigation/geocode", params={"q": "Ahmedabad"}).json()["category"] == "UNKNOWN"
    assert client.get("/api/airports/search", params={"q": "AMD"}).json()["message"].endswith("not configured")
    assert client.get("/api/railways/stations/search", params={"q": "ADI"}).json()["freshness"] == "NOT_AVAILABLE"


def test_validation_rejects_bad_coordinates_and_codes():
    assert client.get("/api/navigation/route", params={"origin": "200,3", "destination": "1,2"}).status_code == 422
    assert client.get("/api/railways/trains/not-a-number").status_code == 422
    assert client.get("/api/airports/!!!/flights").status_code == 422


def test_provider_secrets_are_not_echoed(monkeypatch):
    monkeypatch.setenv("GRAPHHOPPER_API_KEY", "secret-test-value")
    response = client.get("/api/navigation/geocode", params={"q": "Ahmedabad"})
    assert "secret-test-value" not in response.text


def test_mocked_airport_response_normalizes_only_supplied_fields(monkeypatch):
    import app as service

    monkeypatch.setenv("AVIATIONSTACK_ACCESS_KEY", "test-key")
    service.cache.clear()
    calls = []

    async def fake_get_json(url, params, headers=None):
        calls.append((url, params))
        return {"data": [{"airport_name": "Example", "iata_code": "EXA", "latitude": "1.0", "longitude": "2.0"}]}

    monkeypatch.setattr(service, "get_json", fake_get_json)
    first = client.get("/api/airports/search", params={"q": "EXA"}).json()
    assert first["category"] == "LIVE"
    assert first["records"][0]["iata"] == "EXA"
    assert "gate" not in first["records"][0]
    second = client.get("/api/airports/search", params={"q": "EXA"}).json()
    assert second["category"] == "CACHED"
    assert len(calls) == 1

