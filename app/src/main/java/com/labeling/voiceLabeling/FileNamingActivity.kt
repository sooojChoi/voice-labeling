package com.labeling.voiceLabeling

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.media.MediaRecorder
import android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.exifinterface.media.ExifInterface
import com.labeling.voiceLabeling.databinding.ImgPreviewActivityBinding
import com.labeling.voiceLabeling.utils.AudioWriterPCM
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import com.naver.speech.clientapi.SpeechRecognitionResult
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList



class FileNamingActivity: AppCompatActivity() {
    private val binding by lazy { ImgPreviewActivityBinding.inflate(layoutInflater) }
    private val photos = ArrayList<Photo>()
    private var recorder: MediaRecorder? = null
    private var clientId :String = "9zj255drnq"
    private var lang = "Korean" //CLOVA speech recognition 언어코드 (Kor, Eng, Jpn, Chn)... 였지만 지금은 스트리밍 방식으로 바뀜
//    private var apiUrl = "https://naveropenapi.apigw.ntruss.com/recog/v1/stt?lang="+lang
    lateinit var exifMetaData:ExifInterface
    lateinit var locale: Locale  //현재 설정 언어를 알기 위해 필요한 변수
    lateinit var imagePath:String
    var bitmap: Bitmap? = null
    lateinit var txtResult:EditText
    lateinit var btnStart:ImageButton
    lateinit var fileNameBtnText: TextView
    lateinit var mResult:String
    private lateinit var writer: AudioWriterPCM
    private var handler: RecognitionHandler? = null
    private var naverRecognizer: NaverRecognizer? = null
    lateinit var contentUri:Uri
    private var isRecord:Int = 0
    lateinit var songValues:ContentValues
    private lateinit var writeFD:ParcelFileDescriptor
    private var songContentUri: Uri? =null
    private lateinit var fusedLocationClient: FusedLocationProviderClient


    //음성을 녹음하여 파일로 저장한다. 저장된 음성 파일을 clova api를 이용하여 text로 변환한다.
    private fun onRecord(start: Boolean) = if (start) {
        startRecording()
    } else {
        stopRecording()
    }

    private fun startRecording() {
        //이미지 파일 이름이 변경된다면 음성녹음 파일 이름도 바뀐 이미지 파일 이름으로 저장되어야함.

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(writeFD.fileDescriptor)
            setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            setMaxDuration(60000)  //음성파일 길이를 60초로 제한한다.
            setOnInfoListener { mr, what, extra ->
                if(what == MEDIA_RECORDER_INFO_MAX_DURATION_REACHED){
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
                    changeImageFilename(binding.editTextFileName.text.toString())
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
            //apiUrl = "https://naveropenapi.apigw.ntruss.com/recog/v1/stt?lang="+lang
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
                Log.d(TAG, "stop and wait Final Result")
                btnStart.isEnabled = false
                naverRecognizer!!.speechRecognizer!!.stop()
            }
        }

        readMedia()


        //contentUri를 얻어온다.
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        //기본 MediaStore주소와 이미지 id를 조합해서 uri생성
        //일련의 행을 검색한 다음, 그 중 하나를 업데이트하거나 삭제하고자 하는 경우 id를 이용한다.
        contentUri = ContentUris.withAppendedId(
            collection,
            photos[photos.size-1].id
        )

        //해당 uri의 이미지를 가져온다
        //var bitmap: Bitmap? = null
        this.contentResolver.openInputStream(contentUri)?.use {
            bitmap = BitmapFactory.decodeStream(it)
        }
        //사진이 회전되지 않도록 조정하기 위해 exif 객체를 생성.
        //파일이름이 변경된 다음에 또 Exif tag를 변경/저장해야할 일이 생긴다면 readMedia()를 호출하고 다시 ExifInterface 객체를 생성해주어야 한다. imagePath 가 바뀌었기 때문.
        try{
            exifMetaData = ExifInterface(File(imagePath))
        }catch(e: IOException){
            e.printStackTrace()
            Toast.makeText(applicationContext, "ExifInterface Error!", Toast.LENGTH_LONG).show();
        }
        val or = exifMetaData.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_UNDEFINED)

        var resultBitmap:Bitmap? = null
        bitmap?.let { resultBitmap = rotateBitmap(it,or) }

        //처음에는 editText에 "일시"로 저장된 파일 이름을 입력해준다.
        binding.editTextFileName.setText(photos[photos.size-1].name)
        binding.imageView2.setImageBitmap(resultBitmap)

      //  filePath = this.getExternalFilesDir("/")?.absolutePath.toString()
      //  filePath = this.getExternalFilesDir(Environment.DIRECTORY_MUSIC).toString()


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //카메라로 찍은 사진을 외부저장소에 저장할 것이기 때문에 외부저장소 권한 요청, 카메라 권한 요청
            requestMultiplePermission(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION))

            return
        }
        var longitude: Double = 0.0
        var latitude: Double = 0.0
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location? ->
                if(location == null){
                    Snackbar.make(binding.imageView2, "Can't find location", Snackbar.LENGTH_LONG).show()
                }else{
                    val provider: String = location.provider
                    longitude = location.longitude
                    latitude = location.latitude
                    val altitude: Double = location.altitude

                    Log.i(TAG,"longitude = $longitude / latitude = $latitude / altitude = $altitude")

                    exifMetaData.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, GPS.convert(longitude))
                    exifMetaData.setAttribute(ExifInterface.TAG_GPS_LATITUDE,GPS.convert(latitude))
                    exifMetaData.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, GPS.latitudeRef(latitude))
                    exifMetaData.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, GPS.longitudeRef(longitude))
                    exifMetaData.saveAttributes()

                    Log.i(TAG,"longitude = ${exifMetaData.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)}, ${exifMetaData.getAttribute(ExifInterface.TAG_GPS_LATITUDE)}")
                }
            }


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
                    val cursor = contentResolver.query(audioCollection, projection, null, null, null)
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
                val audioName = photos[photos.size-1].name.substring(0,photos[photos.size-1].name.length-4)
                recordNum++

                songValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$audioName.mp3")
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
                songContentUri?.let { it1 -> applicationContext.contentResolver.update(it1,songValues,null,null) }
                0
            }
        }
        //저장버튼을 누르면 파일 이름이 변경된다. 음성파일이 저장되었다면 음성파일 이름도 여기서 바뀐다.
        binding.btnSave.setOnClickListener {
            changeImageFilename(binding.editTextFileName.text.toString())
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

    internal class RecognitionHandler(activity: FileNamingActivity?) : Handler(Looper.getMainLooper()) {
        private val mActivity: WeakReference<FileNamingActivity> = WeakReference<FileNamingActivity>(activity)
        override fun handleMessage(msg: Message) {
            val activity: FileNamingActivity? = mActivity.get()
            activity?.handleMessage(msg)
        }
    }

    //오디오 파일 이름을 변경하는 함수
    private fun changeAudioFilename(filename:String){
        //만약 녹음한 파일이 있다면, 이름을 변경한다.
        if(songContentUri != null){
            songValues.put(MediaStore.Audio.Media.DISPLAY_NAME, "$filename.mp3")
            songContentUri?.let { contentResolver.update(it,songValues,null,null) }
        }
    }
    //사진 파일 이름을 변경하는 함수
    private fun changeImageFilename(filename:String){
        val imageValues = ContentValues()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imageValues.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        }else{

            imageValues.put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
        }
        contentResolver.update(contentUri, imageValues, null, null)
    }
    //사진을 읽어온다.
    private fun readMedia() {
        if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)){
            return
        }

        val projection:Array<String>
        val collection:Uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            projection = arrayOf(MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DISPLAY_NAME,
                MediaStore.Images.ImageColumns.RELATIVE_PATH, "_data")
        } else {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            projection = arrayOf(MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DISPLAY_NAME, "_data")
        }

        val cursor = contentResolver.query(collection, projection, null, null, null)
        photos.clear()   //array 초기화하고
        cursor?.apply {
            val idCol = getColumnIndex(MediaStore.Images.ImageColumns._ID)
            val titleCol = getColumnIndex(MediaStore.Images.ImageColumns.DISPLAY_NAME)
            var pathCol = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                pathCol = getColumnIndex(MediaStore.Images.ImageColumns.RELATIVE_PATH)
            }
            val uriToPath = getColumnIndex("_data")
            while (moveToNext()) {
                val id = getLong(idCol)
                val title = getString(titleCol)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                     val path = getString(pathCol)
                     if(path =="DCIM/Voice Labeling/") {
                         photos.add(Photo(id, title, "image","path"))   //초기화된 array에 넣는다.
                         imagePath = getString(uriToPath)
                     }
                }else{
                    photos.add(Photo(id, title, "image","path"))   //초기화된 array에 넣는다.
                    imagePath = getString(uriToPath)
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
object GPS {
    private val sb = java.lang.StringBuilder(20)

    /**
     * returns ref for latitude which is S or N.
     * @param latitude
     * @return S or N
     */
    fun latitudeRef(latitude: Double): String {
        return if (latitude < 0.0) "S" else "N"
    }

    /**
     * returns ref for latitude which is S or N.
     * @param latitude
     * @return S or N
     */
    fun longitudeRef(longitude: Double): String {
        return if (longitude < 0.0) "W" else "E"
    }

    /**
     * convert latitude into DMS (degree minute second) format. For instance<br></br>
     * -79.948862 becomes<br></br>
     * 79/1,56/1,55903/1000<br></br>
     * It works for latitude and longitude<br></br>
     * @param latitude could be longitude.
     * @return
     */
    @Synchronized
    fun convert(latitude: Double): String {
        var lat = latitude
        lat = Math.abs(lat)
        val degree = lat.toInt()
        lat *= 60.0
        lat -= degree * 60.0
        val minute = lat.toInt()
        lat *= 60.0
        lat -= minute * 60.0
        val second = (lat * 1000.0).toInt()
        sb.setLength(0)
        sb.append(degree)
        sb.append("/1,")
        sb.append(minute)
        sb.append("/1,")
        sb.append(second)
        sb.append("/1000")
        return sb.toString()
    }
}