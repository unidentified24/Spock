package io.github.unidentified24.spock.features

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.core.content.FileProvider
import java.io.File

object ShareFiles {
    fun share(context:Context,file:File,mime:String){
        val uri=FileProvider.getUriForFile(context,context.packageName+".updates",file)
        val intent=Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData=android.content.ClipData.newRawUri(file.name,uri)
        context.startActivity(Intent.createChooser(intent,"Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun file(context:Context,extension:String)=File(context.cacheDir,"sharing").apply{mkdirs()}.resolve("spock-"+java.util.UUID.randomUUID()+"."+extension.also{require(it.matches(Regex("[a-z0-9]{1,12}")))})
    fun card(context:Context,title:String,subtitle:String,lines:List<String>,accent:Int,background:Int):File {
        val bitmap=Bitmap.createBitmap(1200,1400,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(background)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG);paint.color=accent
        val bars=listOf(36f,64f,94f,128f,84f,108f,50f)
        bars.forEachIndexed{i,height->paint.strokeWidth=10f;paint.strokeCap=Paint.Cap.ROUND;canvas.drawLine(90+i*24f,130-height/2,90+i*24f,130+height/2,paint)}
        paint.typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);paint.textSize=34f;canvas.drawText("SPOCK",850f,142f,paint)
        fun wrapped(text:String,y:Float,size:Float,color:Int,maxLines:Int):Float{
            paint.textSize=size;paint.color=color;var current="";var line=0;var top=y
            for(word in text.split(Regex("\\s+"))){val next=if(current.isBlank())word else "$current $word"
                if(paint.measureText(next)>1000&&current.isNotBlank()){canvas.drawText(current,90f,top,paint);top+=size*1.3f;line++;current=word;if(line>=maxLines)return top}else current=next}
            if(current.isNotBlank()&&line<maxLines){canvas.drawText(current.take(150),90f,top,paint);top+=size*1.3f};return top
        }
        var y=wrapped(title,320f,72f,Color.WHITE,3);y=wrapped(subtitle,y+30,32f,accent,2)+80
        for(line in lines.take(8)){y=wrapped(line,y,40f,Color.WHITE,2)+30;if(y>1250)break}
        paint.color=accent;paint.textSize=26f;canvas.drawText("Your music. Your own space.",90f,1320f,paint)
        val target=file(context,"png");try{target.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}finally{bitmap.recycle()};return target
    }
}
