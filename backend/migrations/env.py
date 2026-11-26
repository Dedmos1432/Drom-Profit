from alembic import context
from sqlalchemy import create_engine, pool

from app.db import database_url
from app.models import Base


def migrate(connection):
    context.configure(connection=connection, target_metadata=Base.metadata, compare_type=True)
    with context.begin_transaction():
        context.run_migrations()


if context.is_offline_mode():
    context.configure(
        dialect_name="postgresql", target_metadata=Base.metadata, literal_binds=True,
    )
    with context.begin_transaction():
        context.run_migrations()
elif (connection := context.config.attributes.get("connection")) is not None:
    migrate(connection)
else:
    engine = create_engine(database_url(), poolclass=pool.NullPool, hide_parameters=True)
    try:
        with engine.connect() as connection:
            migrate(connection)
    finally:
        engine.dispose()
