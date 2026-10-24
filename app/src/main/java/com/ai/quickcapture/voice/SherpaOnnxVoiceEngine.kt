package com.ai.quickcapture.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.util.concurrent.Executors

class SherpaOnnxVoiceEngine(private val context: Context) : VoiceRecognitionEngine {

    companion object {
        private const val TAG = "SherpaOnnxEngine"
        private const val SAMPLE_RATE = 16000
        private const val ASSET_DIR = "sherpa-onnx"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerExecutor = Executors.newSingleThreadExecutor()

    private var recognizer: OnlineRecognizer? = null
    private var activeStream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var isInitialized = false

    override fun getEngineName(): String {
        return "Sherpa-ONNX 纯端侧引擎"
    }

    override fun isAvailable(): Boolean {
        if (isInitialized && recognizer != null) return true
        return checkModelAssetsExist()
    }

    private fun checkModelAssetsExist(): Boolean {
        return try {
            val list = context.assets.list(ASSET_DIR) ?: return false
            val required = setOf("encoder.int8.onnx", "decoder.onnx", "joiner.int8.onnx", "tokens.txt")
            required.all { list.contains(it) }
        } catch (e: Exception) {
            Log.w(TAG, "检查 assets 模型文件异常", e)
            false
        }
    }

    @Synchronized
    private fun ensureRecognizer(): Boolean {
        if (recognizer != null) return true

        return try {
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$ASSET_DIR/encoder.int8.onnx",
                        decoder = "$ASSET_DIR/decoder.onnx",
                        joiner = "$ASSET_DIR/joiner.int8.onnx"
                    ),
                    tokens = "$ASSET_DIR/tokens.txt",
                    modelType = "zipformer",
                    numThreads = 2,
                    provider = "cpu"
                ),
                enableEndpoint = true
            )
            recognizer = OnlineRecognizer(assetManager = context.assets, config = config)
            isInitialized = true
            true
        } catch (e: Throwable) {
            Log.e(TAG, "初始化 Sherpa-ONNX OnlineRecognizer 失败", e)
            isInitialized = false
            false
        }
    }

    override fun checkSupportAsync(callback: VoiceRecognitionEngine.SupportCheckCallback) {
        workerExecutor.execute {
            if (!checkModelAssetsExist()) {
                mainHandler.post {
                    callback.onResult(false, "未检测到 Sherpa-ONNX 离线模型包")
                }
                return@execute
            }

            val ready = ensureRecognizer()
            mainHandler.post {
                if (ready) {
                    callback.onResult(true, "Sherpa-ONNX 端侧离线模型就绪 (Zipformer Int8 16kHz)")
                } else {
                    callback.onResult(false, "Sherpa-ONNX 模型加载初始化失败")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startListening(callback: VoiceRecognitionEngine.Callback) {
        workerExecutor.execute {
            if (!ensureRecognizer()) {
                mainHandler.post {
                    callback.onError("Sherpa-ONNX 引擎尚未就绪", false)
                    callback.onVoiceSupportDisabled("模型初始化异常")
                }
                return@execute
            }

            val rec = recognizer ?: return@execute

            try {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                if (minBufferSize <= 0) {
                    mainHandler.post { callback.onError("麦克风采样配置不支持", false) }
                    return@execute
                }

                val bufferSize = (minBufferSize * 2).coerceAtLeast(3200)
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    mainHandler.post { callback.onError("无法启动麦克风录音，请检查权限", false) }
                    audioRecord?.release()
                    audioRecord = null
                    return@execute
                }

                val stream = rec.createStream()
                activeStream = stream

                audioRecord?.startRecording()
                isRecording = true

                mainHandler.post { callback.onListeningStateChanged(true) }

                val shortBuffer = ShortArray(1600) // ~100ms
                val floatBuffer = FloatArray(1600)
                var lastRecognizedText = ""

                while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (read > 0) {
                        for (i in 0 until read) {
                            floatBuffer[i] = shortBuffer[i] / 32768.0f
                        }

                        val samples = if (read == floatBuffer.size) floatBuffer else floatBuffer.copyOf(read)
                        stream.acceptWaveform(samples, SAMPLE_RATE)

                        while (rec.isReady(stream)) {
                            rec.decode(stream)
                        }

                        val currentResult = rec.getResult(stream)
                        val text = currentResult.text.trim()
                        if (text.isNotEmpty() && text != lastRecognizedText) {
                            lastRecognizedText = text
                            mainHandler.post { callback.onPartialResult(text) }
                        }
                    }
                }

                // 录音结束，处理尾音
                stream.inputFinished()
                while (rec.isReady(stream)) {
                    rec.decode(stream)
                }

                val finalResult = rec.getResult(stream)
                val finalText = finalResult.text.trim().ifEmpty { lastRecognizedText }

                mainHandler.post {
                    callback.onListeningStateChanged(false)
                    callback.onFinalResult(finalText)
                }

                stream.release()
                activeStream = null

            } catch (e: SecurityException) {
                mainHandler.post {
                    callback.onListeningStateChanged(false)
                    callback.onError("请授予录音权限以使用端侧语音", false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "端侧录音识别异常", e)
                mainHandler.post {
                    callback.onListeningStateChanged(false)
                    callback.onError("端侧识别异常: " + (e.message ?: "未知错误"), false)
                }
            } finally {
                safeStopAudioRecord()
            }
        }
    }

    override fun stopListening() {
        isRecording = false
        safeStopAudioRecord()
    }

    override fun isListening(): Boolean {
        return isRecording
    }

    private fun safeStopAudioRecord() {
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "停止 AudioRecord 异常", e)
        } finally {
            audioRecord = null
        }
    }

    override fun release() {
        stopListening()
        workerExecutor.execute {
            activeStream?.release()
            activeStream = null
            recognizer?.release()
            recognizer = null
            isInitialized = false
        }
    }
}
