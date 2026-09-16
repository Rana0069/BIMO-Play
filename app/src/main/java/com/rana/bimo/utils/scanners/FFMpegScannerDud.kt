package com.rana.bimo.utils.scanners

import com.rana.bimo.models.SongTempData
import java.io.File

class FFMpegScanner() : MetadataScanner {
    override fun getAllMetadataFromFile(file: File): SongTempData {
        throw NotImplementedError()
    }

    companion object {
        const val VERSION_STRING = "N/A"
    }
}
