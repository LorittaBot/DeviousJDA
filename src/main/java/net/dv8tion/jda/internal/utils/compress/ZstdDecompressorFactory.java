package net.dv8tion.jda.internal.utils.compress;

import dev.freya02.discord.zstd.api.ZstdDecompressor;

import java.io.IOException;

public class ZstdDecompressorFactory implements DecompressorFactory
{
    private final dev.freya02.discord.zstd.api.ZstdDecompressorFactory underlyingFactory;

    public ZstdDecompressorFactory(int bufferSizeHint) throws IOException
    {
        if (bufferSizeHint == -1)
            bufferSizeHint = ZstdDecompressor.DEFAULT_BUFFER_SIZE;
        this.underlyingFactory = ZstdDecompressorFactoryProvider.getInstance(bufferSizeHint);
    }

    @Override
    public Decompressor create()
    {
        return new ZstdDecompressorAdapter(underlyingFactory.create());
    }
}
