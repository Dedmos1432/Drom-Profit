from fastapi import HTTPException


class APIError(HTTPException):
    def __init__(
        self, status_code: int, code: str, message: str, headers: dict[str, str] | None = None
    ):
        super().__init__(status_code, detail=message, headers=headers)
        self.code = code


def not_found() -> APIError:
    return APIError(404, "not_found", "Ресурс не найден")
