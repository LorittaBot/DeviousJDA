/*
 * Copyright 2015 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
