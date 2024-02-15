package com.labeling.voiceLabeling

import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.LinearLayoutManager
import com.labeling.voiceLabeling.databinding.FileActivityBinding
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

//사진들을 파일 이름을 나열하는 형식으로 보여주기 위한 클래스
class ShowNameActivity:AppCompatActivity() {
    private val binding by lazy { FileActivityBinding.inflate(layoutInflater) }
    private val medias = ArrayList<Media>()
    private val resultMedias = ArrayList<Media>()
    private val exifTexts = ArrayList<ExifMetadata>()
    private lateinit var adapter:MyAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

    }

    override fun onResume() {
        super.onResume()
        medias.clear()
        resultMedias.clear()

        readImage()
        readVideo()
        sortMediaDate()  //날짜 시간 순으로 정렬한다.

        adapter = MyAdapter(this, resultMedias)

        binding.recyclerView.adapter = adapter
        //binding.recyclerView.layoutManager = LinearLayoutManager(this)
        val manager = LinearLayoutManager(this)
        binding.recyclerView.setHasFixedSize(true)

        manager.reverseLayout = true
        manager.stackFromEnd = true
        binding.recyclerView.layoutManager = manager
        adapter.notifyDataSetChanged()

        Log.i(TAG,"ShowNameActivity onResume called")

    }


    private fun readImage() {
        if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)){
            return
        }

        val projection:Array<String>
        val imgCollection:Uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imgCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            projection = arrayOf(MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DISPLAY_NAME,
                MediaStore.Images.ImageColumns.RELATIVE_PATH, "_data",MediaStore.Images.ImageColumns.DATE_ADDED)
        } else {
            imgCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            projection = arrayOf(MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DISPLAY_NAME, "_data", MediaStore.Images.ImageColumns.DATE_ADDED)
        }
        val cursor = contentResolver.query(imgCollection, projection, null, null, null)
        medias.clear()   //array 초기화하고
        exifTexts.clear()
        cursor?.apply {
            val idCol = getColumnIndex(MediaStore.Images.ImageColumns._ID)
            val nameCol = getColumnIndex(MediaStore.Images.ImageColumns.DISPLAY_NAME)
            val filepathCol = getColumnIndex("_data")
            val dateCol = getColumnIndex(MediaStore.Images.ImageColumns.DATE_ADDED)

            while (moveToNext()) {
                val id = getLong(idCol)
                val name = getString(nameCol)
                val filePath = getString(filepathCol)
                val date = getString(dateCol)
                var pathCol = 0

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                    pathCol = getColumnIndex(MediaStore.Images.ImageColumns.RELATIVE_PATH)
                    val path = getString(pathCol)
                    if(path =="DCIM/Voice Labeling/"){
                        val dayTime = SimpleDateFormat("yyyyMMddHHmmss")
                        var setDate: Long = date.toLong()
                        setDate *= 1000
                        val dateResult:Long = dayTime.format(Date(setDate)).toLong()

                        //exif 메타데이터를 얻어내서 exifTexts 배열에 넣는다.

                        var photoUri: Uri = Uri.withAppendedPath(imgCollection, id.toString())
                        photoUri = MediaStore.setRequireOriginal(photoUri)

                        try{
                            contentResolver.openInputStream(photoUri)?.use { stream ->
                                var exif = ExifInterface(stream)
                                medias.add(Media(id, name,"image",filePath,dateResult,exif))   //초기화된 array에 넣는다.
                            }
                        }catch(e: IOException) {
                            e.printStackTrace()
                            Toast.makeText(applicationContext, "image ExifInterface Error!", Toast.LENGTH_LONG).show()
                        }
                        //     val exif = ExifInterface(File(filePath))
                        //     exifTexts.add(ExifMetadata(exif))
                    }
                }
                else{
                    val dayTime = SimpleDateFormat("yyyyMMddHHmmss")
                    var setDate: Long = date.toLong()
                    setDate *= 1000
                    val dateResult:Long = dayTime.format(Date(setDate)).toLong()

                    Log.i(TAG,"image data taken = $date, ${ dayTime.format(Date(setDate))}")
                    //exif 메타데이터를 얻어내서 exifTexts 배열에 넣는다.

                    var photoUri: Uri = Uri.withAppendedPath(imgCollection, id.toString())
                    try{
                        contentResolver.openInputStream(photoUri)?.use { stream ->
                            val exif = ExifInterface(stream)
                            medias.add(Media(id, name,"image",filePath,dateResult,exif))   //초기화된 array에 넣는다.
                        }
                    }catch(e: IOException) {
                        e.printStackTrace()
                        Toast.makeText(applicationContext, "image ExifInterface Error!", Toast.LENGTH_LONG).show()
                    }

                 //   val exif = ExifInterface(File(filePath))
                    //  exifTexts.add(ExifMetadata(exif))
                }
            }
            close()
        }
    }

    private fun readVideo() {
        if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)){
            return
        }

        val projection:Array<String>
        val vdoCollection:Uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vdoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            projection = arrayOf(MediaStore.Video.VideoColumns._ID, MediaStore.Video.VideoColumns.DISPLAY_NAME,
                MediaStore.Video.VideoColumns.RELATIVE_PATH, "_data",MediaStore.Images.ImageColumns.DATE_ADDED)
        } else {
            vdoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            projection = arrayOf(MediaStore.Video.VideoColumns._ID, MediaStore.Video.VideoColumns.DISPLAY_NAME, "_data"
            ,MediaStore.Images.ImageColumns.DATE_ADDED)
        }
        val cursor = contentResolver.query(vdoCollection, projection, null, null, null)
        cursor?.apply {
            val idCol = getColumnIndex(MediaStore.Video.VideoColumns._ID)
            val nameCol = getColumnIndex(MediaStore.Video.VideoColumns.DISPLAY_NAME)
            val filepathCol = getColumnIndex("_data")
            val dateCol = getColumnIndex(MediaStore.Images.ImageColumns.DATE_ADDED)

            while (moveToNext()) {
                val id = getLong(idCol)
                val name = getString(nameCol)
                val filePath = getString(filepathCol)
                var pathCol = 0
                val date = getString(dateCol)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                    pathCol = getColumnIndex(MediaStore.Video.VideoColumns.RELATIVE_PATH)
                    val path = getString(pathCol)
                    if(path =="DCIM/Voice Labeling/"){
                        val dayTime = SimpleDateFormat("yyyyMMddHHmmss")
                        var setDate: Long = date.toLong()
                        setDate *= 1000
                        val dateResult:Long = dayTime.format(Date(setDate)).toLong()

                        Log.i(TAG,"video data taken = $date, ${ dayTime.format(Date(setDate))}")
                        //exif 메타데이터를 얻어내서 exifTexts 배열에 넣는다.

                        var videoUri: Uri = Uri.withAppendedPath(vdoCollection, id.toString())
                        videoUri = MediaStore.setRequireOriginal(videoUri)

                        try{
                            contentResolver.openInputStream(videoUri)?.use { stream ->
                                val exif = ExifInterface(stream)
                                medias.add(Media(id, name,"video",filePath,dateResult,exif))   //초기화된 array에 넣는다.
                            }
                        }catch(e: IOException) {
                            e.printStackTrace()
                            Toast.makeText(applicationContext, "video ExifInterface Error!", Toast.LENGTH_LONG).show()
                        }
                        //     val exif = ExifInterface(File(filePath))
                        //     exifTexts.add(ExifMetadata(exif))
                    }
                }
                else{
                    val dayTime = SimpleDateFormat("yyyyMMddHHmmss")
                    var setDate: Long = date.toLong()
                    setDate *= 1000
                    val dateResult:Long = dayTime.format(Date(setDate)).toLong()

                    Log.i(TAG,"video data taken = $date, ${ dayTime.format(Date(setDate))}")

                    //exif 메타데이터를 얻어내서 exifTexts 배열에 넣는다.
                    var videoUri: Uri = Uri.withAppendedPath(vdoCollection, id.toString())
                    try{
                        contentResolver.openInputStream(videoUri)?.use { stream ->
                            val exif = ExifInterface(stream)
                            medias.add(Media(id, name,"video",filePath,dateResult,exif))   //초기화된 array에 넣는다.
                        }
                    }catch(e: IOException) {
                        e.printStackTrace()
                        Toast.makeText(applicationContext, "video ExifInterface Error!", Toast.LENGTH_LONG).show()
                    }

                    //   val exif = ExifInterface(File(filePath))
                    //  exifTexts.add(ExifMetadata(exif))
                }
            }
            close()
        }
    }

    private fun sortMediaDate(){
        //연도 오름차순 정렬
        for(i in 0 until medias.size){  //0부터 size-1까지 포함
            for(j in i+1 until medias.size){
                if(medias[i].date/10000000000 > medias[j].date/10000000000){
                    val temp = medias[i]
                    medias[i] = medias[j]
                    medias[j] = temp
                }
            }
        }
        //month 내림차순 정렬
        for(i in 0 until medias.size){  //0부터 size-1까지 포함
            for(j in i+1 until medias.size){
                if(medias[i].date/100000000 > medias[j].date/100000000){
                    val temp = medias[i]
                    medias[i] = medias[j]
                    medias[j] = temp
                }
            }
        }
        //date 내림차순 정렬
        for(i in 0 until medias.size){  //0부터 size-1까지 포함
            for(j in i+1 until medias.size){
                if(medias[i].date/1000000 > medias[j].date/1000000){
                    val temp = medias[i]
                    medias[i] = medias[j]
                    medias[j] = temp
                }
            }
        }
        //Hours 내림차순 정렬
        for(i in 0 until medias.size){  //0부터 size-1까지 포함
            for(j in i+1 until medias.size){
                if(medias[i].date/10000 > medias[j].date/10000){
                    val temp = medias[i]
                    medias[i] = medias[j]
                    medias[j] = temp
                }
            }
        }
        //minutes 내림차순 정렬
        for(i in 0 until medias.size){  //0부터 size-1까지 포함
            for(j in i+1 until medias.size){
                if(medias[i].date/100 > medias[j].date/100){
                    val temp = medias[i]
                    medias[i] = medias[j]
                    medias[j] = temp
                }
            }
        }
        //seconds 내림차순 정렬
        for(i in 0 until medias.size){  //0부터 size-1까지 포함
            for(j in i+1 until medias.size){
                if(medias[i].date > medias[j].date){
                    val temp = medias[i]
                    medias[i] = medias[j]
                    medias[j] = temp
                }
            }
        }
        for(i in 0 until medias.size){
            resultMedias.add(medias[i])
        }
    }

    private fun hasPermission(perm: String) =
        checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

}