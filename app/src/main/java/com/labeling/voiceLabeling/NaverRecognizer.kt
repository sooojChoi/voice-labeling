package com.labeling.voiceLabeling


import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import androidx.annotation.WorkerThread

import com.naver.speech.clientapi.SpeechConfig;
import com.naver.speech.clientapi.SpeechConfig.EndPointDetectType;
import com.naver.speech.clientapi.SpeechConfig.LanguageType;
import com.naver.speech.clientapi.SpeechRecognitionException;
import com.naver.speech.clientapi.SpeechRecognitionListener;
import com.naver.speech.clientapi.SpeechRecognitionResult;
import com.naver.speech.clientapi.SpeechRecognizer;


internal class NaverRecognizer(context: Context?, handler: Handler, clientId: String?) :
    SpeechRecognitionListener {
    private val mHandler: Handler = handler
    private var mRecognizer: SpeechRecognizer? = null
    val speechRecognizer: SpeechRecognizer?
        get() = mRecognizer

    fun recognize(languageType: String) {
        try {
            if(languageType =="Korean"){
                mRecognizer?.recognize(SpeechConfig(LanguageType.KOREAN, EndPointDetectType.AUTO))
            }else if(languageType == "English"){
                mRecognizer?.recognize(SpeechConfig(LanguageType.ENGLISH, EndPointDetectType.AUTO))
            }
        } catch (e: SpeechRecognitionException) {
            e.printStackTrace()
        }
    }

    @WorkerThread
    override fun onInactive() {
        val msg: Message = Message.obtain(mHandler, R.id.clientInactive)
        msg.sendToTarget()
    }

    @WorkerThread
    override fun onReady() {
        val msg: Message = Message.obtain(mHandler, R.id.clientReady)
        msg.sendToTarget()
    }

    @WorkerThread
    override fun onRecord(speech: ShortArray) {
        val msg: Message = Message.obtain(mHandler, R.id.audioRecording, speech)
        msg.sendToTarget()
    }

    @WorkerThread
    override fun onPartialResult(result: String) {
        val msg: Message = Message.obtain(mHandler, R.id.partialResult, result)
        msg.sendToTarget()
    }

    @WorkerThread
    override fun onEndPointDetected() {
        Log.d(TAG, "Event occurred : EndPointDetected")
    }

    @WorkerThread
    override fun onResult(result: SpeechRecognitionResult) {
        val msg: Message = Message.obtain(mHandler, R.id.finalResult, result)
        msg.sendToTarget()
    }

    @WorkerThread
    override fun onError(errorCode: Int) {
        val msg: Message = Message.obtain(mHandler, R.id.recognitionError, errorCode)
        msg.sendToTarget()
    }

    @WorkerThread
    override fun onEndPointDetectTypeSelected(epdType: EndPointDetectType) {
        val msg: Message = Message.obtain(mHandler, R.id.endPointDetectTypeSelected, epdType)
        msg.sendToTarget()
    }

    companion object {
        private val TAG = NaverRecognizer::class.java.simpleName
    }

    init {
        try {
            mRecognizer = SpeechRecognizer(context, clientId)
        } catch (e: SpeechRecognitionException) {
            e.printStackTrace()
        }
        mRecognizer?.setSpeechRecognitionListener(this)
    }
}