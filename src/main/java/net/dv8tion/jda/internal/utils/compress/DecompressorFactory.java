package net.dv8tion.jda.internal.utils.compress;

import net.dv8tion.jda.api.utils.Compression;

import java.io.IOException;
import java.io.UncheckedIOException;

public interface DecompressorFactory
{
    Decompressor create();

    static DecompressorFactory of(Compression compression, int bufferSizeHint)
    {
        switch (compression)
        {
        case ZLIB:
            return new ZlibDecompressorFactory(bufferSizeHint);
        case ZSTD:
            try
            {
                return new ZstdDecompressorFactory(bufferSizeHint);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException("Unable to create a Zstd decompressor factory", e);
            }
        default:
            throw new IllegalStateException("Unknown compression");
        }
    }
}
