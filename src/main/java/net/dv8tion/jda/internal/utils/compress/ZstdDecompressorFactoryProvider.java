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

import dev.freya02.discord.zstd.api.ZstdDecompressorFactory;
import dev.freya02.discord.zstd.api.ZstdNativesLoader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceLoader;

public class ZstdDecompressorFactoryProvider
{
    private static ZstdDecompressorFactory factory;

    @Nonnull
    public static synchronized ZstdDecompressorFactory getInstance() throws IOException
    {
        if (factory == null)
        {
            Iterator<ZstdDecompressorFactory> factories = ServiceLoader.load(ZstdDecompressorFactory.class).iterator();
            if (!factories.hasNext())
                throw new IllegalStateException("No Zstd decompressor was found, please install one, see https://github.com/freya022/discord-zstd-java");

            ZstdNativesLoader.loadFromJar();

            factory = factories.next();
        }

        return factory;
    }
}
