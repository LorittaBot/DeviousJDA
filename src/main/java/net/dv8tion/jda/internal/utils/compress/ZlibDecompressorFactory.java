package net.dv8tion.jda.internal.utils.compress;

public class ZlibDecompressorFactory implements DecompressorFactory
{
    private final int maxBufferSize;

    public ZlibDecompressorFactory(int maxBufferSize)
    {
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    public Decompressor create()
    {
        return new ZlibDecompressor(maxBufferSize);
    }
}
