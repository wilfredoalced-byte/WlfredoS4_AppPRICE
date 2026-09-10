package com.example.proyecto_acta_prais;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PdfPreviewActivity extends AppCompatActivity {
    private final List<Bitmap> pages=new ArrayList<>();
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);LinearLayout body=Ui.screen(this,"Vista previa del acta","PDF de dos páginas · amplíe con los botones de zoom");
        String path=getIntent().getStringExtra("path");if(path==null){finish();return;}File file=new File(path);
        try{if(!file.getCanonicalPath().startsWith(getCacheDir().getCanonicalPath()+File.separator))throw new Exception("Archivo no autorizado.");}catch(Exception e){Ui.message(this,e.getMessage());return;}
        Ui.button(body,"Volver",v->finish());
        if(getIntent().getBooleanExtra("share",false))Ui.button(body,"Compartir PDF",v->{Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",file);Intent i=new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(android.content.ClipData.newRawUri("Acta PRICE",uri));startActivity(Intent.createChooser(i,"Compartir Acta PRICE"));});
        try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(descriptor)){
            if(renderer.getPageCount()!=2)throw new Exception("El acta debe tener exactamente dos páginas.");
            for(int i=0;i<2;i++){
                Ui.text(body,"Página "+(i+1)+" de 2",16);Bitmap bitmap;
                try(PdfRenderer.Page page=renderer.openPage(i)){bitmap=Bitmap.createBitmap(1190,1684,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);}
                pages.add(bitmap);HorizontalScrollView scroll=new HorizontalScrollView(this);ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setImageBitmap(bitmap);image.setContentDescription("Página "+(i+1)+" del Acta PRICE");scroll.addView(image);body.addView(scroll);
                int width=getResources().getDisplayMetrics().widthPixels-Ui.dp(this,36);image.setLayoutParams(new android.widget.FrameLayout.LayoutParams(width,(int)(width*1.415f)));
                Ui.button(body,"Ampliar / ajustar página "+(i+1),v->{int current=image.getLayoutParams().width;int next=current==width?width*2:width;image.setLayoutParams(new android.widget.FrameLayout.LayoutParams(next,(int)(next*1.415f)));});
            }
        }catch(Exception e){Ui.message(this,"No se pudo abrir el PDF: "+e.getMessage());}
    }
    @Override protected void onDestroy(){super.onDestroy();for(Bitmap b:pages)b.recycle();}
}
