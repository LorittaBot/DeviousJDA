package net.dv8tion.jda.internal.utils.compress;

import java.io.IOException;

public class ZstdDecompressorFactory implements DecompressorFactory
{
    private final dev.freya02.discord.zstd.api.ZstdDecompressorFactory underlyingFactory;

    public ZstdDecompressorFactory(int bufferSizeHint) throws IOException
    {
        this.underlyingFactory = ZstdDecompressorFactoryProvider.getInstance(bufferSizeHint);
    }

    @Override
    public Decompressor create()
    {
        return new ZstdDecompressorAdapter(underlyingFactory.create());
    }
}
