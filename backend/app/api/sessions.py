from hashlib import sha256
from secrets import token_urlsafe
from typing import Annotated

from fastapi import APIRouter, Depends, Request, Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select

from .. import schemas
from ..db import Database, session_for
from ..errors import APIError
from ..models import UserSession


router = APIRouter()
bearer = HTTPBearer(scheme_name="SessionBearer", auto_error=False)


def token_hash(token: str) -> str:
    return sha256(token.encode("utf-8")).hexdigest()


@router.post("/session", status_code=201)
def create_session(response: Response, db: Database) -> schemas.Session:
    token = token_urlsafe(32)
    db.add(UserSession(token_hash=token_hash(token)))
    db.commit()
    response.headers["Cache-Control"] = "no-store"
    return schemas.Session(token=token, token_type="bearer")


def require_session(
    request: Request,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
) -> str:
    if credentials is not None:
        with session_for(request.app.state.engine) as db:
            session_id = db.scalar(
                select(UserSession.id).where(UserSession.token_hash == token_hash(credentials.credentials))
            )
        if session_id is not None:
            return session_id
    raise APIError(
        401, "unauthorized", "Требуется токен сессии", {"WWW-Authenticate": "Bearer"}
    )


Owner = Annotated[str, Depends(require_session)]
