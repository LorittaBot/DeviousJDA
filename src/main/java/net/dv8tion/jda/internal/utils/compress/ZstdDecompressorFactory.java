package net.dv8tion.jda.internal.utils.compress;

import java.io.IOException;

public class ZstdDecompressorFactory implements DecompressorFactory
{
    private final dev.freya02.discord.zstd.api.ZstdDecompressorFactory underlyingFactory;
    private final int bufferSize;

    public ZstdDecompressorFactory(int bufferSize) throws IOException
    {
        this.bufferSize = bufferSize;
        this.underlyingFactory = ZstdDecompressorFactoryProvider.getInstance();
    }

    @Override
    public Decompressor create()
    {
        return new ZstdDecompressorAdapter(underlyingFactory.get(bufferSize));
    }
}
