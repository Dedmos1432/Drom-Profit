"""Forward a published TCP port to a single service on an internal network."""

import asyncio
import sys


async def copy_stream(reader, writer):
    while data := await reader.read(65536):
        writer.write(data)
        await writer.drain()
    writer.write_eof()


async def forward(client_reader, client_writer, host, port):
    upstream_writer = None
    try:
        upstream_reader, upstream_writer = await asyncio.wait_for(
            asyncio.open_connection(host, port), timeout=5,
        )
        async with asyncio.TaskGroup() as streams:
            streams.create_task(copy_stream(client_reader, upstream_writer))
            streams.create_task(copy_stream(upstream_reader, client_writer))
    except* (OSError, TimeoutError):
        pass
    finally:
        client_writer.close()
        if upstream_writer is not None:
            upstream_writer.close()


async def serve(listen_port, host, port):
    server = await asyncio.start_server(
        lambda reader, writer: forward(reader, writer, host, port), "0.0.0.0", listen_port,
    )
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    listen_port, host, port = sys.argv[1:]
    asyncio.run(serve(int(listen_port), host, int(port)))
