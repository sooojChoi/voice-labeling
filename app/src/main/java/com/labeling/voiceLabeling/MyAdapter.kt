package com.labeling.voiceLabeling

import android.app.Dialog
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Address
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.startActivity
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.RecyclerView
import com.labeling.voiceLabeling.databinding.ItemBinding
//import java.io.File
import java.io.IOException


data class Photo(val id:Long, val name:String,val sort:String,val path:String)
data class Media(var id:Long, var name:String, var sort:String, var path:String, var date:Long, var exif:ExifInterface)
data class Video(val id:Long, val name:String)
data class Audio(var id:Long, var name:String, var path:String)
data class ExifMetadata(val filePath:ExifInterface)

class MyViewHolder(val binding: ItemBinding) : RecyclerView.ViewHolder(binding.root)

class MyAdapter(private val context: Context, private val medias: MutableList<Media>) : RecyclerView.Adapter<MyViewHolder>() {
    private val audios = ArrayList<Audio>()
    private var realAudios = ArrayList<Audio>()
    private var dialog=Dialog(context)
    private lateinit var contentUri:Uri
    var itemPosition: Int = 0
    var deleteImageId = 0L
    var deleteAudioId = 0L
    var isDelete = 0
    lateinit var filePath:String
    private lateinit var audioContentUri:Uri
    var isAudio = 0
    var audioPath = "path"
    lateinit var fd: ParcelFileDescriptor

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemBinding.inflate(inflater, parent, false)
        return MyViewHolder(binding)
    }

    override fun getItemViewType(position: Int): Int {
        return position

    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val media = medias[position]
       // val exif = ExifInterface(File(exifText.filePath))
        val exif = media.exif
        val sort = media.sort  //"image" or "video"
        val or = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_UNDEFINED)
        var isMusic = 0
        filePath = media.path

        for(i in 0 until medias.size) {
            realAudios.add(Audio(0L,"defaultName","defaultPath"))
        }

        holder.binding.nameText.text = media.name

        lateinit var collection: Uri
        val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }else{
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        if(media.sort=="image"){
            collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            holder.binding.sortText.text="P "

            /*
            val date = exif.getAttribute(ExifInterface.TAG_DATETIME)
            var dateText = ""
            dateText = if(date !=null){
                date.substring(0,4) +"."+ date.substring(5,7) +"."+ date.substring(8,10) +"."
            }else{
                "no date"
            }*/

            var latLong: DoubleArray
            exif.run {
                // If lat/long is null, fall back to the coordinates (0, 0).
                latLong = this.latLong ?: doubleArrayOf(1.0, 1.0)
            }

            Log.i(TAG,"image metadata MyAdapter = ${latLong[0]}, ${latLong[1]}")
            val geocoder = Geocoder(this.context)
            var list: List<Address>? = null
            try {
                //미리 구해놓은 위도값 mLatitude;
                //미리 구해놓은 경도값 mLongitude;
                list = geocoder.getFromLocation(
                    latLong[0],  // 위도
                    latLong[1],  // 경도
                    10
                ) // 얻어올 값의 개수
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("test", "입출력 오류")
            }
            if (list != null) {
                if (list.isEmpty()) {
                    holder.binding.exifLocateText.setText(R.string.is_address)
                } else {
                    holder.binding.exifLocateText.text = list[0].adminArea + " " + list[0].thoroughfare
                    Log.i(TAG, "list = ${list[0]}")
                }
            }

            holder.binding.imageView.setOnLongClickListener {
                dialog = Dialog(context)
                itemPosition = position
                contentUri = ContentUris.withAppendedId(
                    collection,
                    media.id
                )
                deleteImageId = media.id
                if(holder.binding.isAudioText.text == "♬ "){
                    audioContentUri=ContentUris.withAppendedId(
                        audioCollection,
                        realAudios[itemPosition].id
                    )
                    isAudio=1
                    deleteAudioId = realAudios[itemPosition].id
                }
                Log.i(TAG,"audio name = ${realAudios[itemPosition].name}")
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(R.layout.delete_dialog)
                dialog.show()
                dialogButtonEvent("image")
                true
            }
            holder.binding.imageView.setOnClickListener {
                val intent = Intent(context,ImagePlayActivity::class.java)
                intent.putExtra("id",media.id)
                intent.putExtra("name",media.name.substring(0,media.name.length-4))
                intent.putExtra("or",or)
                if(holder.binding.isAudioText.text == "♬ "){
                    intent.putExtra("musicPath",realAudios[position].path)
                    intent.putExtra("audioId",realAudios[position].id)
                }else{
                    intent.putExtra("musicPath","path")
                }

                startActivity(context,intent,null)
            }

        }else{
            collection = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }else{
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            holder.binding.sortText.text = "M "

            val retriever = MediaMetadataRetriever()

            contentUri = ContentUris.withAppendedId(
                collection,
                media.id
            )
            var list: List<Address>? = null
            try{
                fd = context.contentResolver.openFileDescriptor(contentUri,"w",null)!!
                retriever.setDataSource(fd.fileDescriptor)
                val latlong = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.split('+')
                val lat = latlong?.get(1)
                val long = latlong?.get(2)?.split("/")?.get(0)

                val geocoder = Geocoder(this.context)
                try {
                    //미리 구해놓은 위도값 mLatitude;
                    //미리 구해놓은 경도값 mLongitude;
                    if (lat != null && long !=null) {
                        list = geocoder.getFromLocation(
                            lat.toDouble(),  // 위도
                            long.toDouble(),  // 경도
                            10
                        )
                    } // 얻어올 값의 개수
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.e("test", "입출력 오류")
                }

                retriever.release()
            }catch (e: RecoverableSecurityException){
                holder.binding.exifLocateText.setText(R.string.is_address)
            }
            if (list != null) {
                if (list.isEmpty()) {
                    holder.binding.exifLocateText.setText(R.string.is_address)
                } else {
                    holder.binding.exifLocateText.text = list[0].adminArea + " " + list[0].thoroughfare
                    Log.i(TAG, "list = ${list[0]}")
                }
            }

            holder.binding.imageView.setOnLongClickListener {
                dialog = Dialog(context)
                itemPosition = position
                contentUri = ContentUris.withAppendedId(
                    collection,
                    media.id
                )
                deleteImageId = media.id
                if(holder.binding.isAudioText.text == "♬ "){
                    audioContentUri=ContentUris.withAppendedId(
                        audioCollection,
                        realAudios[itemPosition].id
                    )
                    isAudio=1
                    deleteAudioId = realAudios[itemPosition].id
                }
                Log.i(TAG,"audio name = ${realAudios[itemPosition].name}")
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(R.layout.delete_dialog)
                dialog.show()
                dialogButtonEvent("video")
                true
            }

            holder.binding.imageView.setOnClickListener {
                val intent = Intent(context,VideoPlayActivity::class.java)
                intent.putExtra("id",media.id)
                intent.putExtra("name",media.name.substring(0,media.name.length-4))
                if(holder.binding.isAudioText.text == "♬ "){
                    intent.putExtra("musicPath",realAudios[position].path)
                    intent.putExtra("audioId",realAudios[position].id)
                }else{
                    intent.putExtra("musicPath","path")
                }

                startActivity(context,intent,null)
            }
        }


        //기본 MediaStore 주소와 이미지 id를 조합해서 uri생성
        contentUri = ContentUris.withAppendedId(
            collection,
            media.id
        )

        var thumbnail: Bitmap? = null
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
            thumbnail = context.contentResolver.loadThumbnail(contentUri, Size(480, 480), null)
            Log.i(TAG,"it is working in version Q")

            //얻은 썸네일을 이미지뷰에 넣는다.
            holder.binding.imageView.setImageBitmap(thumbnail)
        }else{
            context.contentResolver.openInputStream(contentUri)?.use {
                var options = BitmapFactory.Options()
                options.inSampleSize = 4
                try{
                    thumbnail = BitmapFactory.decodeStream(it,null,options)!!
                }catch (e:NullPointerException){

                }
            }
            var resultBitmap:Bitmap? = null
            thumbnail?.let { resultBitmap = rotateBitmap(it,or) }

            //얻은 썸네일을 이미지뷰에 넣는다.
            holder.binding.imageView.setImageBitmap(resultBitmap)
        }


        val date = media.date.toString()
        var dateText = date.substring(0,4) +"."+ date.substring(4,6) +"."+ date.substring(6,8) +"."
        holder.binding.exifDateText.text = dateText

        readAudio()

        var music = 0
        var index = 0
        Log.i(TAG,"audio size = ${audios.size}")
        if(audios.size!=0){
            while(music==0){
                if(audios[index].name==media.name.substring(0,media.name.length-4)){
                    holder.binding.isAudioText.text = "♬ "
                    audioPath = audios[index].path
                    realAudios[position].id = audios[index].id
                    realAudios[position].name = audios[index].name
                    realAudios[position].path = audios[index].path
                    music=1
                }
                index++
                if(index>audios.size-1){
                    music=1
                }
            }
        }

    }

    override fun getItemCount(): Int {
        return medias.size
    }
    //파일 삭제를 물어보는 다이얼로그의 버튼 클릭 이벤트 담당 함수
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun dialogButtonEvent(imgOrVideo: String) {
        val yesBtn = dialog.findViewById<Button>(R.id.yesBtn)
        val noBtn = dialog.findViewById<Button>(R.id.noBtn)
        yesBtn.setOnClickListener {
            medias.removeAt(itemPosition)
            notifyItemRemoved(itemPosition)
            notifyItemRangeChanged(itemPosition, medias.size)
            //     notifyDataSetChanged()

            dialog.dismiss()

            context.contentResolver.delete(contentUri, null, null)  //해당 이미지 삭제
            if(isAudio==1){
                context.contentResolver.delete(audioContentUri, null, null)  //해당 이미지 삭제
                isAudio=0
            }
           /* if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                //버전 10 이상이면 파일을 삭제하기 위해서 한번 더 권한요청을 해야한다.
                val intent = Intent(context,ImageDeleteActivity::class.java)
                intent.putExtra("photoId",deleteImageId)
                intent.putExtra("ivsort",imgOrVideo)

                if(isAudio==1){
                    intent.putExtra("audioId",deleteAudioId)
                    intent.putExtra("isAudio",1)
                    isAudio=0
                }

                startActivity(context,intent,null)
            }else{
                context.contentResolver.delete(contentUri, null, null)  //해당 이미지 삭제
                if(isAudio==1){
                    context.contentResolver.delete(audioContentUri, null, null)  //해당 이미지 삭제
                    isAudio=0
                }
            }*/
        }
        noBtn.setOnClickListener {
            dialog.dismiss()
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
    //오디오 파일을 모두 가져온다.
    private fun readAudio() {
        val projection = arrayOf(MediaStore.Audio.AudioColumns._ID, MediaStore.Audio.AudioColumns.DISPLAY_NAME,"_data")
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val cursor = context.contentResolver.query(collection, projection, null, null, null)
        audios.clear()   //array 초기화하고
        cursor?.apply {
            val idCol = getColumnIndex(MediaStore.Audio.AudioColumns._ID)
            val titleCol = getColumnIndex(MediaStore.Audio.AudioColumns.DISPLAY_NAME)
            val path = getColumnIndex("_data")
            while (moveToNext()) {
                val id = getLong(idCol)
                val title = getString(titleCol)
                audios.add(Audio(id,title.substring(0,title.length-4),getString(path)))
            }
            close()
        }
    }

}