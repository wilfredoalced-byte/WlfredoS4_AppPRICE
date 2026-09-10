package com.example.proyecto_acta_prais;

import android.content.Context;
import android.graphics.*;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import java.io.ByteArrayOutputStream;

public class SignatureView extends View {
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private boolean drawn=false;
    public SignatureView(Context c){super(c);pen.setColor(Color.BLACK);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(Ui.dp(c,2));pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);setBackgroundColor(Color.WHITE);setContentDescription("Área para dibujar la firma con el dedo");}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawPath(path,pen);}
    @Override public boolean onTouchEvent(MotionEvent e){getParent().requestDisallowInterceptTouchEvent(true);if(e.getAction()==MotionEvent.ACTION_DOWN){path.moveTo(e.getX(),e.getY());}else if(e.getAction()==MotionEvent.ACTION_MOVE){path.lineTo(e.getX(),e.getY());drawn=true;}else if(e.getAction()==MotionEvent.ACTION_UP){performClick();}invalidate();return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    public void clear(){path.reset();drawn=false;invalidate();}
    public String png(){if(!drawn)return "";Bitmap b=Bitmap.createBitmap(getWidth(),getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);c.drawPath(path,pen);float scale=Math.min(1f,600f/getWidth());Bitmap small=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(getWidth()*scale)),Math.max(1,Math.round(getHeight()*scale)),true);ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();small.recycle();return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);}
}
