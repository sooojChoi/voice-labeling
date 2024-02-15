package com.labeling.voiceLabeling

import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.labeling.voiceLabeling.databinding.ImgPlayActivityBinding
import com.labeling.voiceLabeling.utils.AudioWriterPCM
import com.google.android.material.snackbar.Snackbar
import com.naver.speech.clientapi.SpeechRecognitionResult
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*

class ImagePlayActivity:AppCompatActivity() {
    private val binding by lazy { ImgPlayActivityBinding.inflate(layoutInflater) }
    lateinit var audioPlayer:MediaPlayer
    private var photoId:Long = 0
    private var audioId:Long = 0
    private lateinit var photoName:String
    private var or = 0
    private var audioPath = "path"
    private lateinit var contentUri:Uri
    lateinit var songValues:ContentValues
    private lateinit var writeFD: ParcelFileDescriptor
    private var songContentUri: Uri? =null
    private var recorder: MediaRecorder? = null
    private var clientId :String = "9zj255drnq"
    private var lang = "Korean"
    lateinit var txtResult: EditText
    lateinit var btnStart: ImageButton
    lateinit var fileNameBtnText: TextView
    lateinit var mResult:String
    private lateinit var writer: AudioWriterPCM
    private var handler: ImagePlayActivity.RecognitionHandler? = null
    private var naverRecognizer: NaverRecognizer? = null
    private var isRecord:Int = 0
    lateinit var locale: Locale  //현재 설정 언어를 알기 위해 필요한 변수
    var photonameChanged:String =""
    var recordNum = 0
    var makeRecordFile = 0



    //음성을 녹음하여 파일로 저장한다. 저장된 음성 파일을 clova api를 이용하여 text로 변환한다.
    private fun onRecord(start: Boolean) = if (start) {
        startRecording()
    } else {
        stopRecording()
    }

    private fun startRecording() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(writeFD.fileDescriptor)
            setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            setMaxDuration(60000)  //음성파일 길이를 60초로 제한한다.
            setOnInfoListener { mr, what, extra ->
                if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED){
                    onRecord(false)
                    binding.recordBtnText.setText(R.string.record_start_btn)
                    binding.recordBtn.setImageResource(R.drawable.before_record_btn_not_clicked)
                    Snackbar.make(binding.recordBtn.rootView,R.string.complete_recording,Snackbar.LENGTH_SHORT).show()

                    songValues.clear()
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                        songValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    }
                    songValues.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp3")
                    songContentUri?.let { it1 -> applicationContext.contentResolver.update(it1,songValues,null,null) }
                    isRecord=0
                }
            }

            try {
                prepare()
            } catch (e: IOException) {
                Log.e(TAG, "prepare() failed")
            }
            start()
        }
    }
    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null

    }
    private fun handleMessage(msg: Message) {
        when (msg.what) {
            R.id.clientReady -> {
                txtResult.setText(R.string.connectedText)
                writer =
                    AudioWriterPCM(this.getExternalFilesDir("/")?.absolutePath + "/NaverSpeechTest")
                writer.open("Test")
            }
            R.id.audioRecording -> writer.write(msg.obj as ShortArray)
            R.id.partialResult -> {
                mResult = msg.obj as String
                txtResult.setText(mResult)
            }
            R.id.finalResult -> {
                val speechRecognitionResult = msg.obj as SpeechRecognitionResult
                val results = speechRecognitionResult.results
                val strBuf = StringBuilder()

                //인식이 잘 되지 않은 경우
                if(results == null){
                    //다시 시도해달라는 메시지를 띄움
                    Snackbar.make(binding.imagePlayView, R.string.retry_record, Snackbar.LENGTH_LONG).show()
                    return
                }
                strBuf.append(results[0])
                mResult = strBuf.toString()
                if(mResult.length>50){
                    mResult=""
                    Snackbar.make(binding.imagePlayView, R.string.text_limit, Snackbar.LENGTH_LONG).show()
                }else{
                    txtResult.setText(mResult)
                }

                //버튼을 눌렀으나 말을하지 않아 인식되지 않은 경우
                if(mResult==""){
                    Snackbar.make(binding.imagePlayView, R.string.not_saved, Snackbar.LENGTH_LONG).show()
                }else{//버튼을 누르고 음성인식이 잘 된 경우
                    //음성파일이 저장되었다면 음성파일 이름도 바뀐다.
                    changeImageFilename(mResult)
                    changeAudioFilename(mResult)

                    Snackbar.make(binding.imagePlayView, R.string.saved, Snackbar.LENGTH_LONG).show()
                }
            }
            R.id.recognitionError -> {
                writer.close()

                mResult = "Error code : " + msg.obj.toString()
                txtResult.setText(mResult)
                fileNameBtnText.setText(R.string.str_start)
                btnStart.isEnabled = true
                btnStart.setImageResource(R.drawable.before_record_btn_not_clicked)
            }
            R.id.clientInactive -> {
                writer.close()

                fileNameBtnText.setText(R.string.str_start)
                btnStart.isEnabled = true
                btnStart.setImageResource(R.drawable.before_record_btn_not_clicked)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        audioPlayer = MediaPlayer()

        photoId = intent?.getLongExtra("id",0L)!!
        photoName = intent?.getStringExtra("name") ?: ""
        or = intent?.getIntExtra("or",0) ?: 0
        audioPath = intent?.getStringExtra("musicPath") ?: "path"
        audioId = intent?.getLongExtra("audioId",0L)!!
        photonameChanged = photoName

        txtResult = binding.fnameText  //파일명을 나타낼 EditText
        btnStart = binding.recordFilenameBtn  //파일명 음성인식을 담당하는 Button
        fileNameBtnText = binding.filenameBtnText  //파일이름 음성인식버튼 텍스트
        binding.recordBtnText.setText(R.string.record_start_btn)

        //현재 핸드폰 기본 설정 언어가 무엇인지 알아내기 위함
        locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            applicationContext.resources.configuration.locales.get(0)
        } else{
            applicationContext.resources.configuration.locale
        }

        if(locale.language == "en"){
            lang = "English"
            //apiUrl = "https://naveropenapi.apigw.ntruss.com/recog/v1/stt?lang="+lang
        }

        handler = ImagePlayActivity.RecognitionHandler(this)
        naverRecognizer = NaverRecognizer(this.applicationContext, handler!!,clientId)

        btnStart.setOnClickListener {
            if (!naverRecognizer!!.speechRecognizer!!.isRunning) {
                mResult = ""
                txtResult.setText(R.string.connectingText)
                fileNameBtnText.setText(R.string.str_stop)
                naverRecognizer!!.recognize(lang)
                btnStart.setImageResource(R.drawable.recording_btn_green)
            } else {
                Log.d(TAG, "stop and wait Final Result")
                btnStart.isEnabled = false
                naverRecognizer!!.speechRecognizer!!.stop()
            }
        }


        //contentUri를 얻어온다.
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }else{
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        //기본 MediaStore주소와 이미지 id를 조합해서 uri생성
        //일련의 행을 검색한 다음, 그 중 하나를 업데이트하거나 삭제하고자 하는 경우 id를 이용한다.
        contentUri = ContentUris.withAppendedId(
            collection,
            photoId
        )

        //해당 uri의 이미지를 가져온다
        var bitmap: Bitmap? = null
        this.contentResolver.openInputStream(contentUri)?.use {
            bitmap = BitmapFactory.decodeStream(it)
        }

        var resultBitmap:Bitmap? = null
        bitmap?.let { resultBitmap = rotateBitmap(it,or) }
        binding.imagePlayView.setImageBitmap(resultBitmap)
        binding.settingImageView.setImageBitmap(resultBitmap)
        binding.fnameText.setText(photoName)

        if(audioPath!="path"){
            try{
                audioPlayer = MediaPlayer()
                audioPlayer.setDataSource(audioPath)
                audioPlayer.prepare()
                audioPlayer.start()

                binding.imagePlayView.setOnClickListener {
                    if(recordNum!=0){
                        val projection = arrayOf("_data")
                        val cursor = contentResolver.query(audioCollection, projection, MediaStore.Audio.Media.DISPLAY_NAME +"='$photonameChanged.mp3'", null, null)
                        cursor?.apply {
                            val pathCol = getColumnIndex("_data")
                            while (moveToNext()) {
                                audioPath = getString(pathCol)
                            }
                            close()
                            audioPlayer.reset()
                            audioPlayer.setDataSource(audioPath)
                            audioPlayer.prepare()
                            audioPlayer.start()
                        }
                    }else{
                        audioPlayer.start()
                    }
                }
                binding.settingImageView.setOnClickListener {
                    if(recordNum!=0){
                        val projection = arrayOf("_data")
                        val cursor = contentResolver.query(audioCollection, projection, MediaStore.Audio.Media.DISPLAY_NAME +"='$photonameChanged.mp3'", null, null)
                        cursor?.apply {
                            val pathCol = getColumnIndex("_data")
                            while (moveToNext()) {
                                audioPath = getString(pathCol)
                            }
                            close()
                            audioPlayer.reset()
                            audioPlayer.setDataSource(audioPath)
                            audioPlayer.prepare()
                            audioPlayer.start()
                        }
                    }else{
                        audioPlayer.start()
                    }
                }
            }
            catch (e: FileNotFoundException){
                Snackbar.make(binding.imagePlayView, "no audio file", Snackbar.LENGTH_SHORT).show()
            }

            if(recordNum==0){
                songContentUri = ContentUris.withAppendedId(
                    audioCollection,
                    audioId
                )
                songValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$photonameChanged.mp3")
                    put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp3")
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                        put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/Voice Labeling")
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                }
                recordNum++
            }

        }else{
            Snackbar.make(binding.imagePlayView, "no audio file", Snackbar.LENGTH_SHORT).show()
        }

        binding.recordFilenameBtn.isEnabled = false
        binding.recordBtn.isEnabled = false
        binding.btnSave.isEnabled =false

        binding.recordFilenameBtn.visibility = View.GONE
        binding.recordBtn.visibility = View.GONE
        binding.filenameBtnText.visibility = View.GONE
        binding.recordBtnText.visibility = View.GONE
        binding.btnSave.visibility = View.GONE
        binding.settingImageView.visibility = View.GONE


        binding.moreViewBtn.setOnClickListener {
            if(!binding.recordBtn.isEnabled){
                binding.recordFilenameBtn.isEnabled = true
                binding.recordBtn.isEnabled = true
                binding.btnSave.isEnabled = true

                binding.recordFilenameBtn.visibility = View.VISIBLE
                binding.recordBtn.visibility = View.VISIBLE
                binding.filenameBtnText.visibility = View.VISIBLE
                binding.recordBtnText.visibility = View.VISIBLE
                binding.btnSave.visibility = View.VISIBLE
                binding.settingImageView.visibility = View.VISIBLE
                binding.imagePlayView.visibility = View.GONE

                binding.moreViewBtn.setImageResource(R.drawable.up_arrow)
            }else{
                binding.recordFilenameBtn.isEnabled = false
                binding.recordBtn.isEnabled = false
                binding.filenameBtnText.isActivated = false
                binding.recordBtnText.isActivated = false
                binding.btnSave.isEnabled = false

                binding.recordFilenameBtn.visibility = View.GONE
                binding.recordBtn.visibility = View.GONE
                binding.filenameBtnText.visibility = View.GONE
                binding.recordBtnText.visibility = View.GONE
                binding.btnSave.visibility = View.GONE
                binding.settingImageView.visibility = View.GONE
                binding.imagePlayView.visibility = View.VISIBLE

                binding.moreViewBtn.setImageResource(R.drawable.down_arrow)
            }
        }


        //음성 녹음 버튼
        binding.recordBtn.setOnClickListener {
            isRecord = if(isRecord==0){
                if(recordNum!=0){
                    val projection = arrayOf(MediaStore.Audio.AudioColumns._ID)
                    val cursor = contentResolver.query(audioCollection, projection, MediaStore.Audio.Media.DISPLAY_NAME +"='$photonameChanged.mp3'", null, null)
                    cursor?.apply{
                        val idCol = getColumnIndex(MediaStore.Audio.AudioColumns._ID)
                        while(moveToNext()){
                            audioId = getLong(idCol)
                        }
                        close()
                    }
                    //처음 녹음하는게 아니라면 기존 음성파일을 지우고 다시 만든다.
                    val deleteUri = ContentUris.withAppendedId(
                        audioCollection,
                        audioId
                    )
                    applicationContext.contentResolver.delete(deleteUri,null,null)
                }

                recordNum++
                songValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$photonameChanged.mp3")
                    put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp3")
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                        put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/Voice Labeling")
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                }

                songContentUri = applicationContext.contentResolver.insert(audioCollection,songValues)
                writeFD = applicationContext.contentResolver.openFileDescriptor(songContentUri!!,"w",null)!!

                onRecord(true)
                binding.recordBtnText.setText(R.string.recording_state)
                binding.recordBtn.setImageResource(R.drawable.recording_btn_green)
                makeRecordFile++
                1
            }else{
                onRecord(false)
                binding.recordBtnText.setText(R.string.record_start_btn)
                binding.recordBtn.setImageResource(R.drawable.before_record_btn_not_clicked)
                Snackbar.make(binding.recordBtn.rootView,R.string.complete_recording,Snackbar.LENGTH_SHORT).show()


                songValues.clear()
                if(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q){
                    songValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    songValues.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp3")
                }
                applicationContext.contentResolver.update(songContentUri!!,songValues,null,null)
                if(makeRecordFile==1 && audioPath=="path"){
                    audioPlayer = MediaPlayer()

                    binding.imagePlayView.setOnClickListener {
                        if(recordNum!=0){
                            val projection = arrayOf("_data")
                            val cursor = contentResolver.query(audioCollection, projection, MediaStore.Audio.Media.DISPLAY_NAME +"='$photonameChanged.mp3'", null, null)
                            cursor?.apply {
                                val pathCol = getColumnIndex("_data")
                                while (moveToNext()) {
                                    audioPath = getString(pathCol)
                                }
                                close()
                                audioPlayer.reset()
                                audioPlayer.setDataSource(audioPath)
                                audioPlayer.prepare()
                                audioPlayer.start()
                            }
                        }else{
                            audioPlayer.start()
                        }
                    }
                    binding.settingImageView.setOnClickListener {
                        if(recordNum!=0){
                            val projection = arrayOf("_data")
                            val cursor = contentResolver.query(audioCollection, projection, MediaStore.Audio.Media.DISPLAY_NAME +"='$photonameChanged.mp3'", null, null)
                            cursor?.apply {
                                val pathCol = getColumnIndex("_data")
                                while (moveToNext()) {
                                    audioPath = getString(pathCol)
                                }
                                close()
                                audioPlayer.reset()
                                audioPlayer.setDataSource(audioPath)
                                audioPlayer.prepare()
                                audioPlayer.start()
                            }
                        }else{
                            audioPlayer.start()
                        }
                    }
                }
                0
            }
        }

        //저장버튼을 누르면 파일 이름이 변경된다. 음성파일이 저장되었다면 음성파일 이름도 여기서 바뀐다.
        binding.btnSave.setOnClickListener {
            changeImageFilename(binding.fnameText.text.toString())
            changeAudioFilename(binding.fnameText.text.toString())

            Snackbar.make(binding.fnameText, R.string.saved, Snackbar.LENGTH_LONG).show()
        }
        //editText 글자 수가 50자가 넘어가면 경고문구가 뜬다. 길이 제한 50자로 되어있음.
        if(binding.fnameText.text.length > 50){
            Snackbar.make(binding.fnameText, R.string.text_limit, Snackbar.LENGTH_LONG).show()
        }
    }
    override fun onStart() {
        super.onStart()

        naverRecognizer?.speechRecognizer?.initialize()
    }
    override fun onResume() {
        super.onResume()
        mResult = ""
        fileNameBtnText.setText(R.string.str_start)
        btnStart.isEnabled = true
    }
    override fun onStop() {
        super.onStop()
        audioPlayer.stop()
        audioPlayer.release()

        recorder?.release()
        recorder = null

        naverRecognizer?.speechRecognizer?.release()

        finish()
    }

    internal class RecognitionHandler(activity: ImagePlayActivity?) : Handler(Looper.getMainLooper()) {
        private val mActivity: WeakReference<ImagePlayActivity> = WeakReference<ImagePlayActivity>(activity)
        override fun handleMessage(msg: Message) {
            val activity: ImagePlayActivity? = mActivity.get()
            activity?.handleMessage(msg)
        }
    }

    //오디오 파일 이름을 변경하는 함수
    private fun changeAudioFilename(filename:String){
        //만약 녹음한 파일이 있다면, 이름을 변경한다.
        if(songContentUri != null){
            songValues.clear()
            songValues.put(MediaStore.Audio.Media.DISPLAY_NAME, "$filename.mp3")
            songValues.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp3")
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                songValues.put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/Voice Labeling")
            }

            contentResolver.update(songContentUri!!,songValues,null,null)
          //  songContentUri?.let { contentResolver.update(it,songValues,null,null) }
        }
    }
    //사진 파일 이름을 변경하는 함수
    private fun changeImageFilename(filename:String){
        photonameChanged = filename
        val imageValues = ContentValues()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imageValues.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        }else{

            imageValues.put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
        }
        contentResolver.update(contentUri, imageValues, null, null)
    }
    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> return bitmap
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180F)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180F)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90F)
                matrix.postScale(-1F, 1F)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90F)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90F)
                matrix.postScale(-1F, 1F)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90F)
            else -> return bitmap
        }
        return try {
            val bmRotated =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            bmRotated
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

}