package com.labeling.voiceLabeling

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever.*
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.labeling.voiceLabeling.databinding.ImgPreviewActivityBinding
import com.labeling.voiceLabeling.utils.AudioWriterPCM
import com.google.android.material.snackbar.Snackbar
import com.naver.speech.clientapi.SpeechRecognitionResult
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList


class VideoFileNamingActivity: AppCompatActivity() {
    private val binding by lazy { ImgPreviewActivityBinding.inflate(layoutInflater) }
    private val videos = ArrayList<Video>()
    lateinit var vdoCollection: Uri
    private var recorder: MediaRecorder? = null
    private var clientId :String = "9zj255drnq"
    //    private var clientSecret:String = "vENxr1O5c2JPrTXKktTH10dGT7SlgK6ATCbbrcVI"
    private var lang = "Korean" //CLOVA speech recognition 언어코드 (Kor, Eng, Jpn, Chn)... 였지만 지금은 스트리밍 방식으로 바뀜
    lateinit var locale:Locale
    lateinit var videoPath:String
    lateinit var txtResult: EditText
    lateinit var btnStart: ImageButton
    lateinit var fileNameBtnText: TextView
    lateinit var mResult:String
    private lateinit var writer: AudioWriterPCM
    private var handler: RecognitionHandler? = null
    private var naverRecognizer: NaverRecognizer? = null
    lateinit var contentUri:Uri
    private var isRecord:Int = 0
    lateinit var songValues:ContentValues
    private lateinit var writeFD: ParcelFileDescriptor
    private var songContentUri: Uri? =null


    //음성을 녹음하여 파일로 저장한다. 저장된 음성 파일을 clova api를 이용하여 text로 변환한다.
    private fun onRecord(start: Boolean) = if (start) {
        startRecording()
    } else {
        stopRecording()
    }
    private fun startRecording() {
        //이미지 파일 이름이 변경된다면 음성녹음 파일 이름도 바뀐 이미지 파일 이름으로 저장되어야함.
        readMedia()

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(writeFD.fileDescriptor)
            setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            setMaxDuration(60000)  //음성파일 길이를 60초로 제한한다.
            setOnInfoListener { mr, what, extra ->
                if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED){
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
                Log.e(ContentValues.TAG, "prepare() failed")
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
                /*
                for (result in results) {
                    strBuf.append(result)
                    strBuf.append("\n")
                }*/

                //인식이 잘 되지 않은 경우
                if(results == null){
                    //다시 시도해달라는 메시지를 띄움
                    Snackbar.make(binding.imageView2, R.string.retry_record, Snackbar.LENGTH_LONG).show()
                    return
                }
                strBuf.append(results[0])
                mResult = strBuf.toString()
                if(mResult.length>50){
                    mResult=""
                    Snackbar.make(binding.imageView2, R.string.text_limit, Snackbar.LENGTH_LONG).show()
                }else{
                    txtResult.setText(mResult)
                }

                //버튼을 눌렀으나 말을하지 않아 인식되지 않은 경우
                if(mResult==""){
                    Snackbar.make(binding.imageView2, R.string.not_saved, Snackbar.LENGTH_LONG).show()
                }else{//버튼을 누르고 음성인식이 잘 된 경우
                    //음성파일이 저장되었다면 음성파일 이름도 바뀐다.
                    changeVideoFilename(binding.editTextFileName.text.toString())
                    changeAudioFilename(binding.editTextFileName.text.toString())

                    Snackbar.make(binding.imageView2, R.string.saved, Snackbar.LENGTH_LONG).show()
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

        txtResult = binding.editTextFileName  //파일명을 나타낼 EditText
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
        }

        handler = RecognitionHandler(this)
        naverRecognizer = NaverRecognizer(this.applicationContext, handler!!,clientId)

        btnStart.setOnClickListener {
            if (!naverRecognizer!!.speechRecognizer!!.isRunning) {
                mResult = ""
                txtResult.setText(R.string.connectingText)
                fileNameBtnText.setText(R.string.str_stop)
                naverRecognizer!!.recognize(lang)
                btnStart.setImageResource(R.drawable.recording_btn_green)
            } else {
                Log.d(ContentValues.TAG, "stop and wait Final Result")
                btnStart.isEnabled = false
                naverRecognizer!!.speechRecognizer!!.stop()
            }
        }

        readMedia()

        contentUri = ContentUris.withAppendedId(
            vdoCollection,
            videos[videos.size-1].id
        )


        var thumbnail: Bitmap? = null
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
            thumbnail = contentResolver.loadThumbnail(contentUri, Size(480, 480), null)
            Log.i(ContentValues.TAG,"it is working in version Q")
        }
        binding.imageView2.setImageBitmap(thumbnail)
        binding.editTextFileName.setText(videos[videos.size-1].name)


        var recordNum = 0
        val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }else{
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        //음성 녹음 버튼
        binding.recordBtn.setOnClickListener {
            isRecord = if(isRecord==0){
                var audioId = 0L
                if(recordNum!=0){
                    val projection = arrayOf(MediaStore.Audio.AudioColumns._ID)
                    val cursor = applicationContext.contentResolver.query(audioCollection, projection, null, null, null)
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

                readMedia()
                val audioName = videos[videos.size-1].name.substring(0,videos[videos.size-1].name.length-4)
                recordNum++

                songValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$audioName.mp3")
                    put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp3")
                    put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/Voice Labeling")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                songContentUri = applicationContext.contentResolver.insert(audioCollection,songValues)!!
                writeFD = applicationContext.contentResolver.openFileDescriptor(songContentUri!!,"w",null)!!

                onRecord(true)
                binding.recordBtnText.setText(R.string.recording_state)
                binding.recordBtn.setImageResource(R.drawable.recording_btn_green)
                1
            }else{
                onRecord(false)
                binding.recordBtnText.setText(R.string.record_start_btn)
                binding.recordBtn.setImageResource(R.drawable.before_record_btn_not_clicked)
                Snackbar.make(binding.recordBtn.rootView,R.string.complete_recording,Snackbar.LENGTH_SHORT).show()


                songValues.clear()
                songValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                songValues.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp3")
                songContentUri?.let { it1 -> applicationContext.contentResolver.update(it1,songValues,null,null) }
                0
            }
        }
        //저장버튼을 누르면 파일 이름이 변경된다. 음성파일이 저장되었다면 음성파일 이름도 여기서 바뀐다.
        binding.btnSave.setOnClickListener {
            changeVideoFilename(binding.editTextFileName.text.toString())
            changeAudioFilename(binding.editTextFileName.text.toString())

            Snackbar.make(binding.imageView2, R.string.saved, Snackbar.LENGTH_LONG).show()
        }

        //editText 글자 수가 50자가 넘어가면 경고문구가 뜬다. 길이 제한 50자로 되어있음.
        if(binding.editTextFileName.text.length>50){
            Snackbar.make(binding.imageView2, R.string.text_limit, Snackbar.LENGTH_LONG).show()
        }


    }

    override fun onStart() {
        super.onStart()

        naverRecognizer?.speechRecognizer?.initialize()
    }


    override fun onStop() {
        super.onStop()
        recorder?.release()
        recorder = null

        naverRecognizer?.speechRecognizer?.release()
    }

    override fun onResume() {
        super.onResume()
        mResult = ""
        fileNameBtnText.setText(R.string.str_start)
        btnStart.isEnabled = true
    }

    internal class RecognitionHandler(activity: VideoFileNamingActivity?) : Handler(Looper.getMainLooper()) {
        private val mActivity: WeakReference<VideoFileNamingActivity> = WeakReference<VideoFileNamingActivity>(activity)
        override fun handleMessage(msg: Message) {
            val activity: VideoFileNamingActivity? = mActivity.get()
            activity?.handleMessage(msg)
        }
    }
    //오디오 파일 이름을 변경하는 함수
    private fun changeAudioFilename(filename:String){
        // val audioValues = ContentValues()

        //   audioValues.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp3")
        //만약 녹음한 파일이 있다면, 이름을 변경한다.
        if(songContentUri != null){
            songValues.put(MediaStore.Audio.Media.DISPLAY_NAME, "$filename.mp3")
            songContentUri?.let { applicationContext.contentResolver.update(it,songValues,null,null) }
            Log.i(TAG,"there is songContentUri")
        }else{
            Log.i(TAG,"there isn't songContentUri")
        }
    }
    //사진 파일 이름을 변경하는 함수
    private fun changeVideoFilename(filename:String){
        val videoValues = ContentValues()

        videoValues.put(MediaStore.Video.Media.DISPLAY_NAME, filename)
        applicationContext.contentResolver.update(contentUri, videoValues, null, null)
    }

    private fun readMedia(){
        if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)){
            return
        }

        val vdoProjection:Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vdoCollection =  MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            vdoProjection = arrayOf(MediaStore.Video.VideoColumns._ID,MediaStore.Video.VideoColumns.DISPLAY_NAME, MediaStore.Video.VideoColumns.RELATIVE_PATH,"_data")
        }else{
            vdoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            vdoProjection = arrayOf(MediaStore.Video.VideoColumns._ID,MediaStore.Video.VideoColumns.DISPLAY_NAME,"_data")
        }

        //동영상 정보들을 videos 배열에 넣기
        val vdoCursor = applicationContext.contentResolver.query(vdoCollection,vdoProjection,null,null,null)
        videos.clear()
        vdoCursor?.apply {
            val idCol = getColumnIndex(MediaStore.Video.VideoColumns._ID)
            val titleCol = getColumnIndex(MediaStore.Video.VideoColumns.DISPLAY_NAME)
            var pathCol = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                pathCol = getColumnIndex(MediaStore.Video.VideoColumns.RELATIVE_PATH)
            }
            val uriToPath = getColumnIndex("_data")
            while (moveToNext()){
                val id = getLong(idCol)
                val title = getString(titleCol)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                    val path = getString(pathCol)
                    if(path =="DCIM/Voice Labeling/"){
                        videos.add(Video(id, title))   //초기화된 array에 넣는다.
                        videoPath = getString(uriToPath)
                    }
                }else{
                    videos.add(Video(id, title))   //초기화된 array에 넣는다.
                    videoPath = getString(uriToPath)
                }

            }
            close()
        }
    }

    private fun hasPermission(perm: String) =
        checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    //다중 권한 요청 함수
    @SuppressLint("StringFormatInvalid")
    fun requestMultiplePermission(perms: Array<String>) {
        val requestPerms = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (requestPerms.isEmpty())
            return  //허용받지 못한 권한이 없다면 return

        val requestPermLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val noPerms = it.filter { item -> item.value == false }.keys
            if (noPerms.isNotEmpty()) { // there is a permission which is not granted!
                AlertDialog.Builder(this).apply {
                    setTitle("Warning")
                    setMessage(getString(R.string.no_permission, noPerms.toString()))
                }.show()
            }
        }

        val showRationalePerms = requestPerms.filter {shouldShowRequestPermissionRationale(it)}
        if (showRationalePerms.isNotEmpty()) {
            // you should explain the reason why this app needs the permission.
            AlertDialog.Builder(this).apply {
                setTitle("Reason")
                setMessage(getString(R.string.req_permission_reason, requestPerms))
                setPositiveButton("Allow") { _, _ -> requestPermLauncher.launch(requestPerms.toTypedArray()) }
                setNegativeButton("Deny") { _, _ -> }
            }.show()
        } else {
            requestPermLauncher.launch(requestPerms.toTypedArray())
        }
    }
}

