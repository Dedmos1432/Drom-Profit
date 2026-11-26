package ru.dedmos.dromprofit.data

import android.util.AtomicFile
import java.io.FileNotFoundException

internal interface TextFile {
    fun readOrNull(): String?
    fun write(value: String)
}

internal class PrivateTextFile(private val file: AtomicFile) : TextFile {
    override fun readOrNull(): String? {
        val input = try {
            file.openRead()
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists()) throw error
            return null
        }
        return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    override fun write(value: String) {
        val output = file.startWrite()
        try {
            output.write(value.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}
