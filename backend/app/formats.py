"""Lexical checks for the OpenAPI uri and date-time string formats."""

import re
from calendar import monthrange
from datetime import datetime, timedelta
from ipaddress import IPv6Address


# RFC 3986 permits percent-encoded octets in each of these components.
_ATOM = r"(?:[A-Za-z0-9._~!$&'()*+,;=-]|%[0-9A-Fa-f]{2})"
_PCHAR = rf"(?:{_ATOM}|[:@])"
_URI = re.compile(
    rf"[A-Za-z][A-Za-z0-9+.\-]*:"
    rf"(?://(?:(?:{_ATOM}|:)*@)?"
    rf"(?:\[(?P<literal>[^\]]+)\]|{_ATOM}*)(?::[0-9]*)?(?=/|[?#]|$)|(?!//))"
    rf"(?:{_PCHAR}|/)*(?:\?(?:{_PCHAR}|[/?])*)?(?:#(?:{_PCHAR}|[/?])*)?"
)
_IPV_FUTURE = re.compile(r"[vV][0-9A-Fa-f]+\.[A-Za-z0-9._~!$&'()*+,;=:\-]+")
_DATE_TIME = re.compile(
    r"(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2}):(\d{2})(?:\.\d+)?"
    r"(?:[Zz]|([+-])(\d{2}):(\d{2}))", re.ASCII
)


def validate_uri(value: str) -> str:
    match = _URI.fullmatch(value)
    if match is None:
        raise ValueError("Ожидается абсолютный URI")
    literal = match['literal']
    if literal and not _IPV_FUTURE.fullmatch(literal):
        if '%' in literal:
            raise ValueError("Недопустимый литерал IP")
        IPv6Address(literal)
    return value


def validate_timestamp(value: str) -> str:
    match = _DATE_TIME.fullmatch(value)
    if match is None:
        raise ValueError("Ожидается дата и время RFC 3339")
    year, month, day, hour, minute, second = map(int, match.groups()[:6])
    sign, hours, minutes = match.groups()[6:]
    offset_hours, offset_minutes = int(hours or 0), int(minutes or 0)
    if offset_hours > 23 or offset_minutes > 59 or second > 60:
        raise ValueError("Недопустимое время или смещение")
    # An equivalent Gregorian year also covers year 0000 and offset rollover.
    local = datetime(2000 + year % 400, month, day, hour, minute, min(second, 59))
    if second == 60:
        offset = timedelta(hours=offset_hours, minutes=offset_minutes)
        utc = local + offset if sign == '-' else local - offset
        if (utc.day, utc.hour, utc.minute) != (monthrange(utc.year, utc.month)[1], 23, 59):
            raise ValueError("Недопустимая позиция дополнительной секунды")
    return value
