package br.com.jogatina

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder

/**
 * ImageLoader global com suporte a GIF/WebP animado (teclado + chat + feed).
 * Sem o decoder, o Coil exibe só o 1º quadro como imagem estática.
 * minSdk 33 -> ImageDecoder nativo (API 28+) cobre GIF e WebP animado.
 */
class JogatinaApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(ImageDecoderDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
