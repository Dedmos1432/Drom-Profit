import asyncio
import json

import pytest

from app.services.listings import (
    DEFAULT_FIXTURE_PATH,
    ListingImportError,
    importer_from_env,
    make_importer,
)
from app.services.listings.fixture_provider import FixtureListingProvider


def run(coro):
    return asyncio.run(coro)


def error_code(importer, url):
    with pytest.raises(ListingImportError) as error:
        run(importer(url))
    return error.value.code


def test_demo_importer_returns_fixture_as_contract_dict():
    importer = make_importer("demo")
    listing = run(importer("https://auto.drom.ru/demo/1.html"))
    expected = json.loads(DEFAULT_FIXTURE_PATH.read_text("utf-8"))[0]
    assert listing.as_dict() == expected
    assert listing.source == "demo"
    assert importer.source == "demo"


def test_demo_importer_ignores_query_and_fragment():
    listing = run(make_importer("demo")("https://auto.drom.ru/demo/2.html?from=share#top"))
    assert listing.listing_id == "demo-002"


def test_demo_errors():
    importer = make_importer("demo")
    assert error_code(importer, "https://auto.drom.ru/demo/999.html") == "listing_unavailable"
    assert error_code(importer, "https://moscow.drom.ru/toyota/camry/1234567.html") == "invalid_url"
    assert error_code(importer, "http://auto.drom.ru/demo/1.html") == "invalid_url"
    assert error_code(importer, "not a url") == "invalid_url"


def test_fixture_rejects_non_demo_rows(tmp_path):
    rows = json.loads(DEFAULT_FIXTURE_PATH.read_text("utf-8"))
    rows[0]["source"] = "drom"
    path = tmp_path / "bad.json"
    path.write_text(json.dumps(rows), "utf-8")
    with pytest.raises(ValueError):
        FixtureListingProvider.from_file(path)


def test_fixture_rejects_duplicate_urls(tmp_path):
    rows = json.loads(DEFAULT_FIXTURE_PATH.read_text("utf-8"))
    rows[1]["listing_url"] = rows[0]["listing_url"] + "?dup=1"
    path = tmp_path / "dup.json"
    path.write_text(json.dumps(rows), "utf-8")
    with pytest.raises(ValueError):
        FixtureListingProvider.from_file(path)


def test_env_defaults_to_demo():
    assert importer_from_env({}).source == "demo"


def test_drom_requires_explicit_approval():
    with pytest.raises(RuntimeError):
        importer_from_env({"DROM_LISTINGS_SOURCE": "drom"})
    with pytest.raises(ValueError):
        importer_from_env({"DROM_LISTINGS_SOURCE": "mixed"})


def test_unexpected_provider_errors_become_parse_error():
    class Broken:
        source = "demo"

        async def fetch(self, url):
            raise ValueError("bad data")

    from app.services.listings import ListingImporter

    assert error_code(ListingImporter(Broken()), "https://auto.drom.ru/demo/1.html") == "parse_error"


def test_all_demo_fixtures_keep_the_complete_public_contract():
    expected = json.loads(DEFAULT_FIXTURE_PATH.read_text("utf-8"))
    importer = importer_from_env({})
    actual = [run(importer(row["listing_url"])).as_dict() for row in expected]
    assert len(actual) == 3
    assert actual == expected
    assert all(len(row) == 18 for row in actual)


@pytest.mark.parametrize("hosts", ["", "*.drom.ru", "drom.ru", "sub.city.drom.ru", "other.example"])
def test_approved_flag_still_requires_exact_drom_hosts(hosts):
    with pytest.raises((RuntimeError, ValueError)):
        importer_from_env({
            "DROM_LISTINGS_SOURCE": "drom", "DROM_LISTINGS_ACCESS_APPROVED": "yes",
            "DROM_LISTINGS_HOSTS": hosts,
        })


def test_real_provider_can_be_configured_with_exact_hosts_without_opening_network():
    importer = importer_from_env({
        "DROM_LISTINGS_SOURCE": "drom", "DROM_LISTINGS_ACCESS_APPROVED": "yes",
        "DROM_LISTINGS_HOSTS": "vladivostok.drom.ru, auto.drom.ru",
        "DROM_LISTINGS_REDIRECT_HOSTS": "moscow.drom.ru",
    })
    assert importer.source == "drom"
    assert importer.provider.allowed_hosts.entries == ("auto.drom.ru", "vladivostok.drom.ru")
    assert error_code(importer, "https://moscow.drom.ru/toyota/camry/12345678.html") == "invalid_url"
