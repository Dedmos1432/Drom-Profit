import pytest

from app.services.listings.errors import ListingImportError
from app.services.listings.urls import HostAllowlist, validate_listing_url

HOSTS = HostAllowlist(["auto.drom.ru", "*.drom.ru"])


def test_canonical_url_drops_query_and_fragment():
    result = validate_listing_url(
        "https://Vladivostok.DROM.ru/toyota/camry/123456789.html?utm=x#photo", HOSTS
    )
    assert result.canonical == "https://vladivostok.drom.ru/toyota/camry/123456789.html"
    assert result.listing_id == "123456789"


def test_demo_path_has_no_numeric_listing_id():
    assert validate_listing_url("https://auto.drom.ru/demo/1.html", HOSTS).listing_id is None


@pytest.mark.parametrize(
    "url",
    [
        "",
        "auto.drom.ru/demo/1.html",
        "http://auto.drom.ru/demo/1.html",
        "ftp://auto.drom.ru/demo/1.html",
        "https://user:pass@auto.drom.ru/demo/1.html",
        "https://auto.drom.ru@evil.example/demo/1.html",
        "https://auto.drom.ru:443/demo/1.html",
        "https://auto.drom.ru:8443/demo/1.html",
        "https://evil.example/demo/1.html",
        "https://drom.ru.evil.example/demo/1.html",
        "https://evildrom.ru/demo/1.html",
        "https://drom.ru/demo/1.html",
        "https://a.b.drom.ru/demo/1.html",
        "https://auto.drom.ru./demo/1.html",
        "https://127.0.0.1/demo/1.html",
        "https://[::1]/demo/1.html",
        "https://169.254.169.254/latest/meta-data.html",
        "https://localhost/demo/1.html",
        "https://auto.drom.ru/demo/../admin.html",
        "https://auto.drom.ru/demo/%2e%2e/1.html",
        "https://auto.drom.ru/demo/1",
        "https://auto.drom.ru/",
        "https://auto.drom.ru/demo/1.html\n",
        " https://auto.drom.ru/demo/1.html",
        "https://auto.drom.ru\\@evil.example/1.html",
        "https://аuto.drom.ru/demo/1.html",  # кириллическая «а»
        "https://auto.drom.ru/" + "a" * 2100 + ".html",
    ],
)
def test_rejects_unsafe_or_foreign_urls(url):
    with pytest.raises(ListingImportError) as error:
        validate_listing_url(url, HOSTS)
    assert error.value.code == "invalid_url"


def test_rejects_non_string():
    with pytest.raises(ListingImportError) as error:
        validate_listing_url(None, HOSTS)
    assert error.value.code == "invalid_url"


def test_wildcard_matches_exactly_one_label():
    hosts = HostAllowlist(["*.drom.ru"])
    assert "moscow.drom.ru" in hosts
    assert "drom.ru" not in hosts
    assert "a.moscow.drom.ru" not in hosts
    assert "-bad.drom.ru" not in hosts


@pytest.mark.parametrize("entry", ["", "*.", "ru", "127.0.0.1", "bad host.ru"])
def test_allowlist_rejects_bad_entries(entry):
    with pytest.raises(ValueError):
        HostAllowlist([entry])
