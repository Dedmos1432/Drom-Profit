"""Visible fields and gallery scoped to the matching Drom bulletin page."""

from html.parser import HTMLParser

from .normalize import clean_text


_SPEC_FIELDS = {
    "specification-year": "year",
    "specification-engine": "engine",
    "specification-power": "power_hp",
    "specification-mileage": "mileage_km",
    "specification-drive": "drive_type",
    "specification-transmission": "transmission",
    "specification-complectation": "trim",
}


class DromPageMarkup(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._images: list[str] = []
        self._galleries_seen = self._tables_seen = 0
        self._values: dict[str, set[str]] = {}
        self._specs = self._description = self._city = self._gallery = 0
        self._row: str | None = None
        self._capture: tuple[str, str] | None = None
        self._value_depth = self._hidden = 0
        self._text: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        marker = values.get("data-ftid")
        if tag == "table" and (self._specs or marker == "bulletin-specifications"):
            if not self._specs:
                self._tables_seen += 1
            self._specs += 1
        elif tag == "tr" and self._specs == 1:
            self._row = _SPEC_FIELDS.get(marker or "")

        if tag == "div":
            if self._description or marker == "bulletin-description":
                self._description += 1
            if self._city or (self._description and marker == "city"):
                self._city += 1
            if self._gallery or marker == "bull-page_bull-gallery_thumbnails":
                if not self._gallery:
                    self._galleries_seen += 1
                self._gallery += 1
        if tag == "a" and self._gallery and values.get("data-ga-stats-name") == "gallery-thumbnail":
            href = values.get("href")
            if href:
                self._images.append(href)

        if self._capture:
            if tag == self._capture[0]:
                self._value_depth += 1
            if tag in {"style", "script", "button"}:
                self._hidden += 1
        elif marker == "bulletin-price":
            self._capture = (tag, "price")
            self._value_depth = 1
        elif marker == "value":
            if tag == "td" and self._specs == 1 and self._row:
                self._capture = (tag, self._row)
            elif tag == "span" and self._city:
                self._capture = (tag, "city")
            if self._capture:
                self._value_depth = 1

    def handle_endtag(self, tag: str) -> None:
        if self._capture:
            if tag in {"style", "script", "button"} and self._hidden:
                self._hidden -= 1
            if tag == self._capture[0]:
                self._value_depth -= 1
                if self._value_depth == 0:
                    field = self._capture[1]
                    value = clean_text("".join(self._text))
                    if field == "power_hp" and value:
                        value = value.rstrip(", ")
                    if value:
                        self._values.setdefault(field, set()).add(value)
                    self._capture = None
                    self._hidden = 0
                    self._text.clear()

        if tag == "table" and self._specs:
            self._specs -= 1
            if not self._specs:
                self._row = None
        elif tag == "tr" and self._specs == 1:
            self._row = None
        elif tag == "div":
            if self._description:
                self._description -= 1
            if self._city:
                self._city -= 1
            if self._gallery:
                self._gallery -= 1

    def handle_data(self, data: str) -> None:
        if self._capture and not self._hidden:
            self._text.append(data)

    @property
    def prices(self) -> set[str]:
        return self._values.get("price", set())

    @property
    def images(self) -> list[str]:
        return self._images if self._galleries_seen == 1 else []

    def value(self, field: str) -> str | None:
        if field != "city" and self._tables_seen != 1:
            return None
        values = self._values.get(field)
        return next(iter(values)) if values and len(values) == 1 else None
