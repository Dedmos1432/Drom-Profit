import pytest

from app.formats import IMAGE_COUNT_LIMIT, IMAGE_URL_LIMIT


FRONT = "https://images.example.test/car/front.jpg"
REAR = "https://images.example.test/car/rear.jpg"
SIDE = "https://images.example.test/car/side.jpg"
CAR = {"source": "manual", "brand": "Demo", "model": "Car", "price": 100}


@pytest.mark.parametrize(("photos", "expected"), [
    ({}, []),
    ({"image_url": FRONT}, [FRONT]),
    ({"image_urls": [REAR, FRONT, REAR]}, [REAR, FRONT]),
    ({"image_url": REAR, "image_urls": [REAR, FRONT]}, [REAR, FRONT]),
])
def test_create_and_read_gallery(client, headers, photos, expected):
    saved = client.post("/api/cars", headers=headers, json=CAR | photos)
    assert saved.status_code == 201, saved.text
    item = saved.json()
    assert item["image_url"] == (expected[0] if expected else None)
    assert "image_urls" not in item
    assert client.get("/api/cars", headers=headers).json() == [item]
    assert client.get(f"/api/cars/{item['id']}", headers=headers).json() == item | {"image_urls": expected}


def test_patch_gallery_preserves_money_flags_and_other_photos(client, headers, new_car):
    path = new_car(image_urls=[FRONT, REAR])

    def patch(body):
        response = client.patch(path, headers=headers, json=body)
        assert response.status_code == 200, response.text
        return response.json()

    def detail():
        return client.get(path, headers=headers).json()

    patch({"year": 2014})
    assert detail()["image_urls"] == [FRONT, REAR]
    patch({"expenses_reviewed": True, "benefits_reviewed": True})
    assert patch({"image_url": REAR})["image_url"] == REAR
    assert detail()["image_urls"] == [REAR, FRONT]
    assert patch({"image_url": SIDE})["image_url"] == SIDE
    assert detail()["image_urls"] == [SIDE, REAR, FRONT]
    assert patch({"image_urls": [REAR, REAR, SIDE]})["image_url"] == REAR
    assert detail()["image_urls"] == [REAR, SIDE]
    assert patch({"image_urls": []})["image_url"] is None
    assert detail()["image_urls"] == []
    patch({"image_url": FRONT})
    assert patch({"image_url": None})["image_url"] is None
    result = detail()
    assert result["image_urls"] == []
    assert result["expenses_reviewed"] and result["benefits_reviewed"]


@pytest.mark.parametrize("photos", [
    {"image_url": FRONT, "image_urls": [REAR]},
    {"image_url": FRONT, "image_urls": []},
    {"image_url": None, "image_urls": [REAR]},
    {"image_urls": None},
    {"image_urls": [None]},
    {"image_urls": [FRONT] * (IMAGE_COUNT_LIMIT + 1)},
    {"image_urls": [FRONT + "a" * IMAGE_URL_LIMIT]},
    {"image_urls": ["file:///tmp/photo.jpg"]},
    {"image_urls": ["javascript:void(0)"]},
    {"image_urls": ["http://images.example.test/photo.jpg"]},
    {"image_urls": ["https://127.0.0.1/photo.jpg"]},
    {"image_urls": ["https://dev.localhost/photo.jpg"]},
    {"image_urls": ["/assets/demo.jpg"]},
    {"image_urls": ["/demo/vehicles/../photo.jpg"]},
])
def test_invalid_photo_write_does_not_change_car(client, headers, new_car, photos):
    path = new_car(image_urls=[FRONT, REAR])
    for response in (
        client.post("/api/cars", headers=headers, json=CAR | photos),
        client.patch(path, headers=headers, json=photos | {"price": 222}),
    ):
        assert response.status_code == 422 and response.json()["code"] == "validation_error"
    before = client.get(path, headers=headers).json()
    assert before["image_urls"] == [FRONT, REAR] and before["price"] != 222
    assert len(client.get("/api/cars", headers=headers).json()) == 1


def test_legacy_patch_cannot_drop_photos_at_capacity(client, headers, new_car):
    photos = [f"{FRONT}?n={number}" for number in range(IMAGE_COUNT_LIMIT)]
    path = new_car(image_urls=photos)
    response = client.patch(path, headers=headers, json={"image_url": REAR})
    assert response.status_code == 422
    assert client.get(path, headers=headers).json()["image_urls"] == photos
