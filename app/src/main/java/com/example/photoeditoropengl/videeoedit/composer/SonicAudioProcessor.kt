package com.example.photoeditoropengl.videeoedit.composer

import android.util.Log
import kotlin.math.min

internal class SonicAudioProcessor(
    sampleRate: Int,
    numChannels: Int
) {
    private lateinit var inputBuffer: ShortArray
    private lateinit var outputBuffer: ShortArray
    private lateinit var pitchBuffer: ShortArray
    private lateinit var downSampleBuffer: ShortArray

    var speed: Float

    var volume: Float

    var pitch: Float
    private var rate: Float
    private var oldRatePosition: Int
    private var newRatePosition: Int

    var chordPitch: Boolean

    var quality: Int

    var numChannels: Int = 0
        private set
    private var inputBufferSize = 0
    private var pitchBufferSize = 0
    private var outputBufferSize = 0
    private var numInputSamples = 0
    private var numOutputSamples = 0
    private var numPitchSamples = 0
    private var minPeriod = 0
    private var maxPeriod = 0
    private var maxRequired = 0
    private var remainingInputToCopy = 0

    var sampleRate: Int = 0
        private set
    private var prevPeriod = 0
    private var prevMinDiff = 0
    private var minDiff = 0
    private var maxDiff = 0

    init {
        allocateStreamBuffers(sampleRate, numChannels)
        speed = 1.0f
        pitch = 1.0f
        volume = 1.0f
        rate = 1.0f
        oldRatePosition = 0
        newRatePosition = 0
        chordPitch = false
        quality = 0
    }

    private fun resize(
        oldArray: ShortArray,
        newLength: Int
    ): ShortArray {
        var newLength = newLength
        newLength *= numChannels
        val newArray = ShortArray(newLength)
        val length = if (oldArray.size <= newLength) oldArray.size else newLength

        System.arraycopy(oldArray, 0, newArray, 0, length)
        return newArray
    }

    private fun move(
        dest: ShortArray,
        destPos: Int,
        source: ShortArray?,
        sourcePos: Int,
        numSamples: Int
    ) {
        System.arraycopy(
            source,
            sourcePos * numChannels,
            dest,
            destPos * numChannels,
            numSamples * numChannels
        )
    }

    private fun scaleSamples(
        samples: ShortArray,
        position: Int,
        numSamples: Int,
        volume: Float
    ) {
        val fixedPointVolume = (volume * 4096.0f).toInt()
        val start = position * numChannels
        val stop = start + numSamples * numChannels

        for (xSample in start until stop) {
            var value = (samples[xSample] * fixedPointVolume) shr 12
            if (value > 32767) {
                value = 32767
            } else if (value < -32767) {
                value = -32767
            }
            samples[xSample] = value.toShort()
        }
    }

    fun getRate(): Float {
        return rate
    }

    fun setRate(
        rate: Float
    ) {
        this.rate = rate
        this.oldRatePosition = 0
        this.newRatePosition = 0
    }

    private fun allocateStreamBuffers(
        sampleRate: Int,
        numChannels: Int
    ) {
        this.sampleRate = sampleRate
        this.numChannels = numChannels
        minPeriod = sampleRate / SONIC_MAX_PITCH
        maxPeriod = sampleRate / SONIC_MIN_PITCH
        maxRequired = 2 * maxPeriod
        inputBufferSize = maxRequired
        inputBuffer = ShortArray(maxRequired * numChannels)
        outputBufferSize = maxRequired
        outputBuffer = ShortArray(maxRequired * numChannels)
        pitchBufferSize = maxRequired
        pitchBuffer = ShortArray(maxRequired * numChannels)
        downSampleBuffer = ShortArray(maxRequired)
        oldRatePosition = 0
        newRatePosition = 0
        prevPeriod = 0
    }

    private fun enlargeOutputBufferIfNeeded(
        numSamples: Int
    ) {
        if (numOutputSamples + numSamples > outputBufferSize) {
            outputBufferSize += (outputBufferSize shr 1) + numSamples
            outputBuffer = resize(outputBuffer, outputBufferSize)
        }
    }

    private fun enlargeInputBufferIfNeeded(
        numSamples: Int
    ) {
        if (numInputSamples + numSamples > inputBufferSize) {
            inputBufferSize += (inputBufferSize shr 1) + numSamples
            inputBuffer = resize(inputBuffer, inputBufferSize)
        }
    }

    private fun addFloatSamplesToInputBuffer(
        samples: FloatArray,
        numSamples: Int
    ) {
        if (numSamples == 0) {
            return
        }
        enlargeInputBufferIfNeeded(numSamples)
        var xBuffer = numInputSamples * numChannels
        for (xSample in 0 until numSamples * numChannels) {
            inputBuffer[xBuffer++] = (samples[xSample] * 32767.0f).toInt().toShort()
        }
        numInputSamples += numSamples
    }

    private fun addShortSamplesToInputBuffer(
        samples: ShortArray?,
        numSamples: Int
    ) {
        if (numSamples == 0) {
            return
        }
        enlargeInputBufferIfNeeded(numSamples)
        move(inputBuffer, numInputSamples, samples, 0, numSamples)
        numInputSamples += numSamples
    }

    private fun addUnsignedByteSamplesToInputBuffer(
        samples: ByteArray,
        numSamples: Int
    ) {
        var sample: Short

        enlargeInputBufferIfNeeded(numSamples)
        var xBuffer = numInputSamples * numChannels
        for (xSample in 0 until numSamples * numChannels) {
            sample =
                ((samples[xSample].toInt() and 0xff) - 128).toShort()
            inputBuffer[xBuffer++] = (sample.toInt() shl 8).toShort()
        }
        numInputSamples += numSamples
    }

    private fun addBytesToInputBuffer(
        inBuffer: ByteArray,
        numBytes: Int
    ) {
        val numSamples = numBytes / (2 * numChannels)
        var sample: Short

        enlargeInputBufferIfNeeded(numSamples)
        var xBuffer = numInputSamples * numChannels
        var xByte = 0
        while (xByte + 1 < numBytes) {
            sample =
                ((inBuffer[xByte].toInt() and 0xff) or (inBuffer[xByte + 1].toInt() shl 8)).toShort()
            inputBuffer[xBuffer++] = sample
            xByte += 2
        }
        numInputSamples += numSamples
    }

    private fun removeInputSamples(
        position: Int
    ) {
        val remainingSamples = numInputSamples - position

        move(inputBuffer, 0, inputBuffer, position, remainingSamples)
        numInputSamples = remainingSamples
    }

    private fun copyToOutput(
        samples: ShortArray,
        position: Int,
        numSamples: Int
    ) {
        enlargeOutputBufferIfNeeded(numSamples)
        move(outputBuffer, numOutputSamples, samples, position, numSamples)
        numOutputSamples += numSamples
    }

    private fun copyInputToOutput(
        position: Int
    ): Int {
        val numSamples =
            min(maxRequired.toDouble(), remainingInputToCopy.toDouble()).toInt()

        copyToOutput(inputBuffer, position, numSamples)
        remainingInputToCopy -= numSamples
        return numSamples
    }

    private fun readFloatFromStream(
        samples: FloatArray,
        maxSamples: Int
    ): Int {
        var numSamples = numOutputSamples
        var remainingSamples = 0

        if (numSamples == 0) {
            return 0
        }
        if (numSamples > maxSamples) {
            remainingSamples = numSamples - maxSamples
            numSamples = maxSamples
        }
        for (xSample in 0 until numSamples * numChannels) {
            samples[xSample] = (outputBuffer[xSample]) / 32767.0f
        }
        move(outputBuffer, 0, outputBuffer, numSamples, remainingSamples)
        numOutputSamples = remainingSamples
        return numSamples
    }


    fun readShortFromStream(
        samples: ShortArray,
        maxSamples: Int
    ): Int {
        var numSamples = numOutputSamples
        var remainingSamples = 0

        if (numSamples == 0) {
            return 0
        }
        if (numSamples > maxSamples) {
            remainingSamples = numSamples - maxSamples
            numSamples = maxSamples
        }
        move(samples, 0, outputBuffer, 0, numSamples)
        move(outputBuffer, 0, outputBuffer, numSamples, remainingSamples)
        numOutputSamples = remainingSamples
        return numSamples
    }


    private fun readUnsignedByteFromStream(
        samples: ByteArray,
        maxSamples: Int
    ): Int {
        var numSamples = numOutputSamples
        var remainingSamples = 0

        if (numSamples == 0) {
            return 0
        }
        if (numSamples > maxSamples) {
            remainingSamples = numSamples - maxSamples
            numSamples = maxSamples
        }
        for (xSample in 0 until numSamples * numChannels) {
            samples[xSample] = ((outputBuffer[xSample].toInt() shr 8) + 128).toByte()
        }
        move(outputBuffer, 0, outputBuffer, numSamples, remainingSamples)
        numOutputSamples = remainingSamples
        return numSamples
    }

    private fun readBytesFromStream(
        outBuffer: ByteArray,
        maxBytes: Int
    ): Int {
        val maxSamples = maxBytes / (2 * numChannels)
        var numSamples = numOutputSamples
        var remainingSamples = 0

        if (numSamples == 0 || maxSamples == 0) {
            return 0
        }
        if (numSamples > maxSamples) {
            remainingSamples = numSamples - maxSamples
            numSamples = maxSamples
        }
        for (xSample in 0 until numSamples * numChannels) {
            val sample = outputBuffer[xSample]
            outBuffer[xSample shl 1] = (sample.toInt() and 0xff).toByte()
            outBuffer[(xSample shl 1) + 1] = (sample.toInt() shr 8).toByte()
        }
        move(outputBuffer, 0, outputBuffer, numSamples, remainingSamples)
        numOutputSamples = remainingSamples
        return 2 * numSamples * numChannels
    }

    fun flushStream() {
        val remainingSamples = numInputSamples
        val s = speed / pitch
        val r = rate * pitch
        val expectedOutputSamples =
            numOutputSamples + ((remainingSamples / s + numPitchSamples) / r + 0.5f).toInt()

        enlargeInputBufferIfNeeded(remainingSamples + 2 * maxRequired)
        for (xSample in 0 until 2 * maxRequired * numChannels) {
            inputBuffer[remainingSamples * numChannels + xSample] = 0
        }
        numInputSamples += 2 * maxRequired
        writeShortToStream(null, 0)
        if (numOutputSamples > expectedOutputSamples) {
            numOutputSamples = expectedOutputSamples
        }
        numInputSamples = 0
        remainingInputToCopy = 0
        numPitchSamples = 0
    }

    fun samplesAvailable(): Int {
        return numOutputSamples
    }

    private fun downSampleInput(
        samples: ShortArray,
        position: Int,
        skip: Int
    ) {
        var position = position
        val numSamples = maxRequired / skip
        val samplesPerValue = numChannels * skip
        var value: Int

        position *= numChannels
        for (i in 0 until numSamples) {
            value = 0
            for (j in 0 until samplesPerValue) {
                value += samples[position + i * samplesPerValue + j].toInt()
            }
            value /= samplesPerValue
            downSampleBuffer[i] = value.toShort()
        }
    }

    private fun findPitchPeriodInRange(
        samples: ShortArray,
        position: Int,
        minPeriod: Int,
        maxPeriod: Int
    ): Int {
        var position = position
        var bestPeriod = 0
        var worstPeriod = 255
        var minDiff = 1
        var maxDiff = 0

        position *= numChannels
        for (period in minPeriod..maxPeriod) {
            var diff = 0
            for (i in 0 until period) {
                val sVal = samples[position + i]
                val pVal = samples[position + period + i]
                diff += if ((sVal >= pVal)) sVal - pVal else pVal - sVal
            }

            if (diff * bestPeriod < minDiff * period) {
                minDiff = diff
                bestPeriod = period
            }
            if (diff * worstPeriod > maxDiff * period) {
                maxDiff = diff
                worstPeriod = period
            }
        }
        this.minDiff = minDiff / bestPeriod
        this.maxDiff = maxDiff / worstPeriod

        return bestPeriod
    }

    private fun prevPeriodBetter(
        minDiff: Int,
        maxDiff: Int,
        preferNewPeriod: Boolean
    ): Boolean {
        if (minDiff == 0 || prevPeriod == 0) {
            return false
        }
        if (preferNewPeriod) {
            if (maxDiff > minDiff * 3) {
                return false
            }
            if (minDiff * 2 <= prevMinDiff * 3) {
                return false
            }
        } else {
            if (minDiff <= prevMinDiff) {
                return false
            }
        }
        return true
    }

    private fun findPitchPeriod(
        samples: ShortArray,
        position: Int,
        preferNewPeriod: Boolean
    ): Int {
        var period: Int
        var skip = 1

        if (sampleRate > SONIC_AMDF_FREQ && quality == 0) {
            skip = sampleRate / SONIC_AMDF_FREQ
        }
        if (numChannels == 1 && skip == 1) {
            period = findPitchPeriodInRange(samples, position, minPeriod, maxPeriod)
        } else {
            downSampleInput(samples, position, skip)
            period = findPitchPeriodInRange(
                downSampleBuffer, 0, minPeriod / skip,
                maxPeriod / skip
            )
            if (skip != 1) {
                period *= skip
                var minP = period - (skip shl 2)
                var maxP = period + (skip shl 2)
                if (minP < minPeriod) {
                    minP = minPeriod
                }
                if (maxP > maxPeriod) {
                    maxP = maxPeriod
                }
                if (numChannels == 1) {
                    period = findPitchPeriodInRange(samples, position, minP, maxP)
                } else {
                    downSampleInput(samples, position, 1)
                    period = findPitchPeriodInRange(downSampleBuffer, 0, minP, maxP)
                }
            }
        }
        val retPeriod = if (prevPeriodBetter(minDiff, maxDiff, preferNewPeriod)) {
            prevPeriod
        } else {
            period
        }
        prevMinDiff = minDiff
        prevPeriod = period
        return retPeriod
    }


    private fun overlapAddWithSeparation(
        numSamples: Int,
        numChannels: Int,
        separation: Int,
        out: ShortArray,
        outPos: Int,
        rampDown: ShortArray,
        rampDownPos: Int,
        rampUp: ShortArray,
        rampUpPos: Int
    ) {
        for (i in 0 until numChannels) {
            var o = outPos * numChannels + i
            var u = rampUpPos * numChannels + i
            var d = rampDownPos * numChannels + i

            for (t in 0 until numSamples + separation) {
                if (t < separation) {
                    out[o] = (rampDown[d] * (numSamples - t) / numSamples).toShort()
                    d += numChannels
                } else if (t < numSamples) {
                    out[o] =
                        ((rampDown[d] * (numSamples - t) + rampUp[u] * (t - separation)) / numSamples).toShort()
                    d += numChannels
                    u += numChannels
                } else {
                    out[o] = (rampUp[u] * (t - separation) / numSamples).toShort()
                    u += numChannels
                }
                o += numChannels
            }
        }
    }

    private fun moveNewSamplesToPitchBuffer(
        originalNumOutputSamples: Int
    ) {
        val numSamples = numOutputSamples - originalNumOutputSamples

        if (numPitchSamples + numSamples > pitchBufferSize) {
            pitchBufferSize += (pitchBufferSize shr 1) + numSamples
            pitchBuffer = resize(pitchBuffer, pitchBufferSize)
        }
        move(pitchBuffer, numPitchSamples, outputBuffer, originalNumOutputSamples, numSamples)
        numOutputSamples = originalNumOutputSamples
        numPitchSamples += numSamples
    }

    private fun removePitchSamples(
        numSamples: Int
    ) {
        if (numSamples == 0) {
            return
        }
        move(pitchBuffer, 0, pitchBuffer, numSamples, numPitchSamples - numSamples)
        numPitchSamples -= numSamples
    }

    private fun adjustPitch(
        originalNumOutputSamples: Int
    ) {
        var period: Int
        var newPeriod: Int
        var separation: Int
        var position = 0

        if (numOutputSamples == originalNumOutputSamples) {
            return
        }
        moveNewSamplesToPitchBuffer(originalNumOutputSamples)
        while (numPitchSamples - position >= maxRequired) {
            period = findPitchPeriod(pitchBuffer, position, false)
            newPeriod = (period / pitch).toInt()
            enlargeOutputBufferIfNeeded(newPeriod)
            if (pitch >= 1.0f) {
                overlapAdd(
                    newPeriod, numChannels, outputBuffer, numOutputSamples, pitchBuffer,
                    position, pitchBuffer, position + period - newPeriod
                )
            } else {
                separation = newPeriod - period
                Log.d("audio r", "adjustPitch: ")
                overlapAddWithSeparation(
                    period, numChannels, separation, outputBuffer, numOutputSamples,
                    pitchBuffer, position, pitchBuffer, position
                )
            }
            numOutputSamples += newPeriod
            position += period
        }
        removePitchSamples(position)
    }


    private fun getSign(value: Int): Int {
        return if (value >= 0) 1 else -1
    }

    private fun interpolate(
        `in`: ShortArray,
        inPos: Int,  // Index to first sample which already includes channel offset.
        oldSampleRate: Int,
        newSampleRate: Int
    ): Short {
        val left = `in`[inPos]
        val right = `in`[inPos + numChannels]
        val position = newRatePosition * oldSampleRate
        val leftPosition = oldRatePosition * newSampleRate
        val rightPosition = (oldRatePosition + 1) * newSampleRate
        val ratio = rightPosition - position
        val width = rightPosition - leftPosition
        return ((ratio * left + (width - ratio) * right) / width).toShort()
    }

    private fun adjustRate(
        rate: Float,
        originalNumOutputSamples: Int
    ) {
        if (numOutputSamples == originalNumOutputSamples) {
            return
        }

        var newSampleRate = (sampleRate / rate).toInt()
        var oldSampleRate = sampleRate

        while (newSampleRate > (1 shl 14) || oldSampleRate > (1 shl 14)) {
            newSampleRate = newSampleRate shr 1
            oldSampleRate = oldSampleRate shr 1
        }

        moveNewSamplesToPitchBuffer(originalNumOutputSamples)
        var position = 0
        while (position < numPitchSamples - 1) {
            while ((oldRatePosition + 1) * newSampleRate > newRatePosition * oldSampleRate) {
                enlargeOutputBufferIfNeeded(1)
                for (i in 0 until numChannels) {
                    outputBuffer[numOutputSamples * numChannels + i] = interpolate(
                        pitchBuffer,
                        position * numChannels + i, oldSampleRate, newSampleRate
                    )
                }
                newRatePosition++
                numOutputSamples++
            }
            oldRatePosition++
            if (oldRatePosition == oldSampleRate) {
                oldRatePosition = 0
                if (newRatePosition != newSampleRate) {
                    System.out.printf("Assertion failed: newRatePosition != newSampleRate\n")
                    assert(false)
                }
                newRatePosition = 0
            }
            position++
        }
        removePitchSamples(numPitchSamples - 1)
    }


    private fun skipPitchPeriod(
        samples: ShortArray,
        position: Int,
        speed: Float,
        period: Int
    ): Int {
        val newSamples: Int

        if (speed >= 2.0f) {
            newSamples = (period / (speed - 1.0f)).toInt()
        } else {
            newSamples = period
            remainingInputToCopy = (period * (2.0f - speed) / (speed - 1.0f)).toInt()
        }
        enlargeOutputBufferIfNeeded(newSamples)
        overlapAdd(
            newSamples, numChannels, outputBuffer, numOutputSamples, samples, position,
            samples, position + period
        )
        numOutputSamples += newSamples
        return newSamples
    }

    private fun insertPitchPeriod(
        samples: ShortArray,
        position: Int,
        speed: Float,
        period: Int
    ): Int {
        val newSamples: Int

        if (speed < 0.5f) {
            newSamples = (period * speed / (1.0f - speed)).toInt()
        } else {
            newSamples = period
            remainingInputToCopy = (period * (2.0f * speed - 1.0f) / (1.0f - speed)).toInt()
        }
        enlargeOutputBufferIfNeeded(period + newSamples)
        move(outputBuffer, numOutputSamples, samples, position, period)
        overlapAdd(
            newSamples, numChannels, outputBuffer, numOutputSamples + period, samples,
            position + period, samples, position
        )
        numOutputSamples += period + newSamples
        return newSamples
    }


    private fun changeSpeed(speed: Float) {
        if (numInputSamples < maxRequired) {
            return
        }
        val numSamples = numInputSamples
        var position = 0
        do {
            if (remainingInputToCopy > 0) {
                position += copyInputToOutput(position)
            } else {
                val period = findPitchPeriod(inputBuffer, position, true)
                position += if (speed > 1.0) {
                    period + skipPitchPeriod(inputBuffer, position, speed, period)
                } else {
                    insertPitchPeriod(inputBuffer, position, speed, period)
                }
            }
        } while (position + maxRequired <= numSamples)

        removeInputSamples(position)
    }

    private fun processStreamInput() {
        val originalNumOutputSamples = numOutputSamples
        val s = speed / pitch
        var r = rate

        if (!chordPitch) {
            r *= pitch
        }
        if (s > 1.00001 || s < 0.99999) {
            changeSpeed(s)
        } else {
            copyToOutput(inputBuffer, 0, numInputSamples)
            numInputSamples = 0
        }
        if (chordPitch) {
            if (pitch != 1.0f) {
                adjustPitch(originalNumOutputSamples)
            }
        } else if (r != 1.0f) {
            adjustRate(r, originalNumOutputSamples)
        }
        if (volume != 1.0f) {
            scaleSamples(
                outputBuffer, originalNumOutputSamples, numOutputSamples - originalNumOutputSamples,
                volume
            )
        }
    }

    fun writeFloatToStream(
        samples: FloatArray,
        numSamples: Int
    ) {
        addFloatSamplesToInputBuffer(samples, numSamples)
        processStreamInput()
    }

    fun writeShortToStream(
        samples: ShortArray?,
        numSamples: Int
    ) {
        addShortSamplesToInputBuffer(samples, numSamples)
        processStreamInput()
    }


    private fun writeUnsignedByteToStream(
        samples: ByteArray,
        numSamples: Int
    ) {
        addUnsignedByteSamplesToInputBuffer(samples, numSamples)
        processStreamInput()
    }

    private fun writeBytesToStream(
        inBuffer: ByteArray,
        numBytes: Int
    ) {
        addBytesToInputBuffer(inBuffer, numBytes)
        processStreamInput()
    }

    private fun sonicChangeShortSpeed(
        samples: ShortArray,
        numSamples: Int,
        speed: Float,
        pitch: Float,
        rate: Float,
        volume: Float,
        useChordPitch: Boolean,
        sampleRate: Int,
        numChannels: Int
    ): Int {
        var numSamples = numSamples
        val stream = SonicAudioProcessor(sampleRate, numChannels)

        stream.speed = speed
        stream.pitch = pitch
        stream.setRate(rate)
        stream.volume = volume
        stream.chordPitch = useChordPitch
        stream.writeShortToStream(samples, numSamples)
        stream.flushStream()
        numSamples = stream.samplesAvailable()
        stream.readShortFromStream(samples, numSamples)
        return numSamples
    }

    companion object {
        private const val SONIC_MIN_PITCH = 65
        private const val SONIC_MAX_PITCH = 400

        private const val SONIC_AMDF_FREQ = 4000

        private fun overlapAdd(
            frameCount: Int,
            channelCount: Int,
            out: ShortArray,
            outPosition: Int,
            rampDown: ShortArray,
            rampDownPosition: Int,
            rampUp: ShortArray,
            rampUpPosition: Int
        ) {
            for (i in 0 until channelCount) {
                var o = outPosition * channelCount + i
                var u = rampUpPosition * channelCount + i
                var d = rampDownPosition * channelCount + i
                for (t in 0 until frameCount) {
                    out[o] =
                        ((rampDown[d] * (frameCount - t) + rampUp[u] * t) / frameCount).toShort()
                    o += channelCount
                    d += channelCount
                    u += channelCount
                }
            }
        }

        private fun changeFloatSpeed(
            samples: FloatArray,
            numSamples: Int,
            speed: Float,
            pitch: Float,
            rate: Float,
            volume: Float,
            useChordPitch: Boolean,
            sampleRate: Int,
            numChannels: Int
        ): Int {
            var numSamples = numSamples
            val stream = SonicAudioProcessor(sampleRate, numChannels)

            stream.speed = speed
            stream.pitch = pitch
            stream.setRate(rate)
            stream.volume = volume
            stream.chordPitch = useChordPitch
            stream.writeFloatToStream(samples, numSamples)
            stream.flushStream()
            numSamples = stream.samplesAvailable()
            stream.readFloatFromStream(samples, numSamples)
            return numSamples
        }
    }
}
