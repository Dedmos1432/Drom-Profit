FROM python:3.14.0-slim-bookworm AS runtime
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /srv/backend
COPY requirements.txt ./
RUN python -m pip --isolated install --no-cache-dir --only-binary=:all: \
        --index-url https://pypi.org/simple -r requirements.txt \
    && groupadd --gid 10001 backend \
    && useradd --uid 10001 --gid backend --create-home backend
COPY app ./app
COPY migrations ./migrations
COPY alembic.ini ./
USER 10001:10001
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

FROM runtime AS test
USER root
COPY requirements-dev.txt ./
RUN python -m pip --isolated install --no-cache-dir --only-binary=:all: \
        --index-url https://pypi.org/simple -r requirements-dev.txt
COPY tests ./tests
USER 10001:10001
CMD ["python", "-m", "pytest", "-q", "-p", "no:cacheprovider"]
