"""Проверка и нормализация ссылки на объявление.

Проверка чисто лексическая и выполняется до любого сетевого обращения:
только https, без учетных данных, без явного порта, хост из точного allowlist,
путь объявления ``*.html`` из безопасного набора символов. Query и fragment
отбрасываются — канонический URL используется как ключ и как адрес запроса.
"""

import re
from collections.abc import Iterable
from dataclasses import dataclass
from urllib.parse import urlsplit

from .errors import ListingImportError

MAX_URL_LENGTH = 2048
_LABEL = re.compile(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
_PATH = re.compile(r"(?:/[A-Za-z0-9_.-]+)+\.html")
_LISTING_ID = re.compile(r"/(\d{4,12})\.html$")


@dataclass(frozen=True, slots=True)
class ListingUrl:
    canonical: str
    host: str
    path: str
    listing_id: str | None  # числовой ID из пути, если он есть

    def matches_listing(self, other: "ListingUrl") -> bool:
        if self.listing_id is not None:
            return self.listing_id == other.listing_id
        return self.canonical == other.canonical


class HostAllowlist:
    """Точный список хостов.

    Элемент ``*.drom.ru`` разрешает ровно одну метку перед суффиксом
    (``vladivostok.drom.ru``), но не сам ``drom.ru`` и не ``a.b.drom.ru``.
    """

    def __init__(self, entries: Iterable[str]):
        exact, suffixes = set(), set()
        for entry in entries:
            entry = entry.strip().lower()
            if entry.startswith("*."):
                suffix = entry[2:]
                _check_hostname(suffix)
                suffixes.add(suffix)
            else:
                _check_hostname(entry)
                exact.add(entry)
        if not exact and not suffixes:
            raise ValueError("Allowlist хостов пуст")
        self._exact = frozenset(exact)
        self._suffixes = frozenset(suffixes)

    def __contains__(self, host: object) -> bool:
        if not isinstance(host, str):
            return False
        if host in self._exact:
            return True
        label, _, rest = host.partition(".")
        return rest in self._suffixes and _LABEL.fullmatch(label) is not None

    @property
    def entries(self) -> tuple[str, ...]:
        return (*sorted(self._exact), *sorted(f"*.{suffix}" for suffix in self._suffixes))

    def __repr__(self) -> str:
        return f"HostAllowlist({list(self.entries)!r})"


def _check_hostname(host: str) -> None:
    labels = host.split(".")
    if len(labels) < 2 or not all(_LABEL.fullmatch(label) for label in labels):
        raise ValueError(f"Недопустимый хост в allowlist: {host!r}")
    if labels[-1].isdigit():
        raise ValueError(f"IP-адрес не может быть в allowlist: {host!r}")


def validate_listing_url(url: object, allowed_hosts: HostAllowlist) -> ListingUrl:
    """Возвращает нормализованную ссылку или бросает ``ListingImportError('invalid_url')``."""
    if not isinstance(url, str) or not url or len(url) > MAX_URL_LENGTH:
        raise ListingImportError("invalid_url", "пустая или слишком длинная строка")
    if not url.isascii() or any(ch.isspace() or ord(ch) < 0x20 or ord(ch) == 0x7F for ch in url):
        raise ListingImportError("invalid_url", "недопустимые символы")
    if "\\" in url:
        raise ListingImportError("invalid_url", "обратная косая черта")
    try:
        parts = urlsplit(url)
        port = parts.port
    except ValueError:
        raise ListingImportError("invalid_url", "не разбирается как URL") from None
    if parts.scheme != "https":
        raise ListingImportError("invalid_url", "разрешен только https")
    if "@" in parts.netloc or parts.username is not None or parts.password is not None:
        raise ListingImportError("invalid_url", "учетные данные в URL")
    if port is not None or ":" in parts.netloc:
        raise ListingImportError("invalid_url", "явный порт")
    host = (parts.hostname or "").lower()
    if parts.netloc.lower() != host or host not in allowed_hosts:
        raise ListingImportError("invalid_url", "хост не разрешен")
    path = parts.path
    if not _PATH.fullmatch(path) or "/../" in path + "/" or "/./" in path + "/":
        raise ListingImportError("invalid_url", "путь не похож на страницу объявления")
    match = _LISTING_ID.search(path)
    listing_id = match.group(1) if match else None
    return ListingUrl(
        canonical=f"https://{host}{path}", host=host, path=path, listing_id=listing_id
    )
