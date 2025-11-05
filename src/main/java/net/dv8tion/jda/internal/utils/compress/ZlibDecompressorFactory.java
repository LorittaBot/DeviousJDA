package net.dv8tion.jda.internal.utils.compress;

import net.dv8tion.jda.internal.utils.Checks;

public class ZlibDecompressorFactory implements DecompressorFactory
{
    private final int maxBufferSize;

    public ZlibDecompressorFactory(int bufferSizeHint)
    {
        if (bufferSizeHint == -1)
        {
            this.maxBufferSize = 2048;
        }
        else
        {
            Checks.notNegative(bufferSizeHint, "Buffer size hint");
            this.maxBufferSize = bufferSizeHint;
        }
    }

    @Override
    public Decompressor create()
    {
        return new ZlibDecompressor(maxBufferSize);
    }
}
