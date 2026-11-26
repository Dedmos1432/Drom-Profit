from collections.abc import Awaitable, Callable

from pydantic import ValidationError

from ..schemas import ListingData
from .listings import ImportErrorCode
from .listings import ListingData as ParserListingData
from .listings import ListingImportError as ParserImportError
from .listings import importer_from_env


ListingImporter = Callable[[str], Awaitable[ListingData]]


class ListingImportError(Exception):
    def __init__(self, code: ImportErrorCode):
        super().__init__(code)
        self.code = code


def make_parser_importer() -> ListingImporter:
    return adapt_parser(importer_from_env())


def adapt_parser(parser: Callable[[str], Awaitable[ParserListingData]]) -> ListingImporter:
    async def import_listing(url: str) -> ListingData:
        try:
            data = await parser(url)
        except ParserImportError as error:
            raise ListingImportError(error.code) from error
        if not isinstance(data, ParserListingData):
            raise ListingImportError("parse_error")
        try:
            return ListingData.model_validate(data.as_dict())
        except ValidationError as error:
            raise ListingImportError("parse_error") from error

    return import_listing
