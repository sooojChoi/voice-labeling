package com.labeling.voiceLabeling

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.labeling.voiceLabeling.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {
    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val photos = ArrayList<Photo>()
    private val videos = ArrayList<Video>()
    var realUri: Uri? = null
    lateinit var imgCollection: Uri
    lateinit var vdoCollection: Uri
    lateinit var requestPermLauncher:ActivityResultLauncher<Array<String>>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //카메라로 찍은 사진을 외부저장소에 저장할 것이기 때문에 외부저장소 권한 요청, 카메라 권한 요청
        requestMultiplePermission(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))


        //사진촬영 후 주어진 uri에 이미지가 저장됨. image를 표시하는 새로운 액티비티 실행함.
        val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
            if (success) {
                // The image was saved into the given Uri -> do something with it
                //새로운 액티비티를 만들어서 실행시키고 그 layout의 imageView에 사진 적용?

                val intent = Intent(this,FileNamingActivity::class.java)

                startActivity(intent)
            }
            else{
                //사진을 찍지 않고 그냥 뒤로가기 누르면(사진이 촬영되지 않으면), 만들었던 uri를 delete한다.
                //전역변수 photos에 파일 이름과 id를 모두 입력
                readMedia()

                //기본 MediaStore주소와 이미지 id를 조합해서 uri생성
                //일련의 행을 검색한 다음, 그 중 하나를 업데이트하거나 삭제하고자 하는 경우 id를 이용한다.
                val deleteUri = ContentUris.withAppendedId(
                    imgCollection,
                    photos[photos.size-1].id
                )

                //사진촬영이 되지 않았기 때문에 만들어졌던 uri를 delete한다.
                contentResolver.delete(deleteUri,null,null)
            }
        }
        val takeVideo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            if(it.resultCode== Activity.RESULT_OK){
                //비디오 촬영 되면 할 것 구현
                val intent = Intent(this,VideoFileNamingActivity::class.java)

                startActivity(intent)

            }
            else{
                //비디오 촬영이 되지 않았을 때..만들어진 uri를 delete한다.
                readMedia()

                val deleteUri = ContentUris.withAppendedId(
                    vdoCollection,
                    videos[videos.size-1].id
                )
                contentResolver.delete(deleteUri,null,null)

            }
        }

        //사진 촬영 버튼을 누르면 사진 촬영 시작
        binding.pictureBtn.setOnClickListener {
            Log.i(TAG,"button clicked")

            //권한이 허용되지 않으면 어플에 접근이 불가능하다는 dialog를 띄운다.
           if(!hasPermission(Manifest.permission.CAMERA) || !hasPermission(Manifest.permission.RECORD_AUDIO)||!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)){
               Log.i(TAG,"permission is not connected")
               AlertDialog.Builder(this).apply {
                   setTitle("Warning")
                   setMessage(getString(R.string.need_permission))
               }.show()
           }else{
               Log.i(TAG,"permission is connected")
               //uri를 생성하여 사진촬영 시작
               createImageUri(newImageName(), "image/jpg")?.let {
                       uri ->
                   realUri = uri
                   takePicture.launch(uri)
               }
               Log.i(TAG,"realUri = $realUri")
           }
        }
        //비디오 촬영 버튼을 누르면 비디오 촬영 시작
        binding.videoBtn.setOnClickListener {
            //권한이 허용되지 않으면 어플에 접근이 불가능하다는 dialog를 띄운다.
            if(!hasPermission(Manifest.permission.CAMERA) || !hasPermission(Manifest.permission.RECORD_AUDIO) ||!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)){
                AlertDialog.Builder(this).apply {
                    setTitle("Warning")
                    setMessage(getString(R.string.need_permission))
                }.show()
            }else{
                //openVideoCam()
                val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)

                createVideoUri(newVideoName(),"vedio/mp4")?.let {
                        uri ->
                    realUri = uri
                    intent.putExtra(MediaStore.EXTRA_OUTPUT,realUri)
                    takeVideo.launch(intent)
                }
            }
        }
        //filename 버튼을 누르면 파일 이름을 나열하여 보여줌
        binding.galleryBtn.setOnClickListener {
            //권한이 허용되지 않으면 어플에 접근이 불가능하다는 dialog를 띄운다.
            if(!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) || !hasPermission(Manifest.permission.RECORD_AUDIO)){
                AlertDialog.Builder(this).apply {
                    setTitle("Warning")
                    setMessage(getString(R.string.need_permission))
                }.show()
            }else{
                startActivity(Intent(this, ShowNameActivity::class.java))
            }
        }
    }


    //촬영한 비디오를 저장할 Uri를 미디어스토어에 생성하고 반환한다.
    private fun createVideoUri(filename: String, mimeType: String):Uri?{
        val values = ContentValues()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        values.put(MediaStore.Video.Media.DATE_ADDED,filename)
        values.put(MediaStore.Video.Media.DISPLAY_NAME,filename)
        values.put(MediaStore.Video.Media.MIME_TYPE,mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH,"DCIM/Voice Labeling")
        }
        return applicationContext.contentResolver.insert(collection,values)
    }
    //파일 이름을 생성하고 반환한다.
    private fun newVideoName():String{
        val sdf = SimpleDateFormat("yyyMMdd_HHmmss")
        val filename = sdf.format(System.currentTimeMillis())
        return "$filename.mp4"
    }

    //촬영한 이미지를 저장할 Uri를 미디어스토어에 생성하고 반환한다.
    private fun createImageUri(filename: String, mimeType: String):Uri?{
        val values = ContentValues()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        values.put(MediaStore.Images.Media.DISPLAY_NAME,filename)
        values.put(MediaStore.Images.Media.TITLE,filename)
        values.put(MediaStore.Images.Media.MIME_TYPE,mimeType)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH,"DCIM/Voice Labeling")
        }
        return applicationContext.contentResolver.insert(collection,values)
    }

    //파일 이름을 생성하고 반환한다.
    private fun newImageName():String{
        val sdf = SimpleDateFormat("yyyMMdd_HHmmss")
        val filename = sdf.format(System.currentTimeMillis())
        return "$filename.jpg"
    }

    private fun readMedia() {
        if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)){
            return
        }

        imgCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        vdoCollection = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }else{
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        //이미지 정보들을 photos 배열에 넣기
        val imgProjection = arrayOf(MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.TITLE)
        val imgCursor = contentResolver.query(imgCollection, imgProjection, null, null, null)
        photos.clear()   //array 초기화하고
        imgCursor?.apply {
            val idCol = getColumnIndex(MediaStore.Images.ImageColumns._ID)
            val titleCol = getColumnIndex(MediaStore.Images.ImageColumns.TITLE)
            while (moveToNext()) {
                val id = getLong(idCol)
                val title = getString(titleCol)
                photos.add(Photo(id, title,"image","path"))   //초기화된 array에 넣는다.
            }
            close()
        }
        //동영상 정보들을 videos 배열에 넣기
        val vdoProjection = arrayOf(MediaStore.Video.VideoColumns._ID,MediaStore.Video.VideoColumns.DISPLAY_NAME)
        val vdoCursor = contentResolver.query(vdoCollection,vdoProjection,null,null,null)
        videos.clear()
        vdoCursor?.apply {
            val idCol = getColumnIndex(MediaStore.Video.VideoColumns._ID)
            val titleCol = getColumnIndex(MediaStore.Video.VideoColumns.DISPLAY_NAME)
            while (moveToNext()){
                val id = getLong(idCol)
                val title = getString(titleCol)
                videos.add(Video(id,title))
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

        requestPermLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
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

    //단일 권한 요청 함수
    @SuppressLint("StringFormatInvalid")
    private fun requestSinglePermission(permission: String) { // 한번에 하나의 권한만 요청하는 예제
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) // 권한 유무 확인
            return
        val requestPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { // 권한 요청 컨트랙트
            if (it == false) { // permission is not granted!
                AlertDialog.Builder(this).apply {
                    setTitle("Warning")
                    setMessage(getString(R.string.no_permission, permission))
                }.show()
            }
        }
        if (shouldShowRequestPermissionRationale(permission)) { // 권한 설명 필수 여부 확인
            AlertDialog.Builder(this).apply {
                setTitle("Reason")
                setMessage(getString(R.string.req_permission_reason, permission))
                setPositiveButton("Allow") { _, _ -> requestPermLauncher.launch(permission) }
                setNegativeButton("Deny") { _, _ -> }
            }.show()
        } else {
            requestPermLauncher.launch(permission) // 권한 요청 시작
        }
    }


}