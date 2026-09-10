package com.example.proyecto_acta_prais;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class LoginActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Session session=new Session(this);
        if(session.valid()) { open();return; }
        LinearLayout body=Ui.screen(this,"PRICE","Fiscalización de precios de combustibles");
        LinearLayout card=Ui.card(body);

        LinearLayout titleRow=new LinearLayout(this);titleRow.setOrientation(LinearLayout.HORIZONTAL);titleRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView fuel=new ImageView(this);fuel.setImageResource(R.drawable.ic_fuel);
        LinearLayout.LayoutParams fuelLp=new LinearLayout.LayoutParams(Ui.dp(this,38),Ui.dp(this,38));fuelLp.rightMargin=Ui.dp(this,12);titleRow.addView(fuel,fuelLp);
        View bar=new View(this);bar.setBackgroundColor(ContextCompat.getColor(this,R.color.accent_700));
        LinearLayout.LayoutParams barLp=new LinearLayout.LayoutParams(Ui.dp(this,4),Ui.dp(this,38));barLp.rightMargin=Ui.dp(this,12);titleRow.addView(bar,barLp);
        LinearLayout titleCol=Ui.column(this);
        TextView loginTitle=new TextView(this);loginTitle.setText("Iniciar sesión");loginTitle.setTextSize(20);loginTitle.setTypeface(null,Typeface.BOLD);loginTitle.setTextColor(ContextCompat.getColor(this,R.color.navy_900));titleCol.addView(loginTitle);
        TextView loginSub=new TextView(this);loginSub.setText("Ingresa tus credenciales para continuar.");loginSub.setTextSize(13);loginSub.setTextColor(ContextCompat.getColor(this,R.color.slate_500));loginSub.setPadding(0,Ui.dp(this,2),0,0);titleCol.addView(loginSub);
        titleRow.addView(titleCol,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(titleRow);

        EditText user=Ui.field(card,R.drawable.ic_user,"Usuario","",InputType.TYPE_CLASS_TEXT);
        EditText password=Ui.field(card,R.drawable.ic_lock,"Contraseña","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_eye,0);
        password.setCompoundDrawablePadding(Ui.dp(this,8));
        password.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_UP) {
                Drawable end=password.getCompoundDrawables()[2];
                if(end!=null && event.getX()>=password.getWidth()-password.getPaddingEnd()-end.getBounds().width()) {
                    boolean hidden=(password.getInputType()&InputType.TYPE_TEXT_VARIATION_PASSWORD)!=0;
                    int selection=password.getSelectionEnd();
                    if(hidden) { password.setInputType(InputType.TYPE_CLASS_TEXT); password.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_eye_off,0); }
                    else { password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); password.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_eye,0); }
                    if(selection>=0) password.setSelection(selection);
                    return true;
                }
            }
            return false;
        });

        Ui.infoBox(card,"Ingresa con la cuenta registrada en el servidor.");
        TextView status=Ui.text(card,"",13);
        Button login=Ui.button(card,"Ingresar",null);

        LinearLayout config=Ui.card(body);
        LinearLayout configTitle=new LinearLayout(this);configTitle.setOrientation(LinearLayout.HORIZONTAL);configTitle.setGravity(Gravity.CENTER_VERTICAL);
        ImageView link=new ImageView(this);link.setImageResource(R.drawable.ic_link);
        LinearLayout.LayoutParams linkLp=new LinearLayout.LayoutParams(Ui.dp(this,22),Ui.dp(this,22));linkLp.rightMargin=Ui.dp(this,8);configTitle.addView(link,linkLp);
        TextView configLabel=new TextView(this);configLabel.setText("Conexión con el servidor");configLabel.setTextSize(17);configLabel.setTypeface(null,Typeface.BOLD);configLabel.setTextColor(ContextCompat.getColor(this,R.color.navy_900));configTitle.addView(configLabel);
        config.addView(configTitle);

        EditText url=Ui.field(config,"URL de la API",session.url(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        Ui.infoBox(config,"Emulador: http://10.0.2.2:8000/\nTeléfono: use la IP de su computadora en la misma red Wi-Fi.");

        login.setOnClickListener(v->{
            if(Ui.value(user).isEmpty()||Ui.value(password).isEmpty()) { Ui.message(this,"Ingrese usuario y contraseña.");return; }
            try { session.setUrl(Ui.value(url)); } catch(Exception e) { Ui.message(this,e.getMessage());return; }
            login.setEnabled(false);status.setText("Validando usuario…");
            Repository repo=new Repository(this);
            String loginUser=Ui.value(user),loginPassword=Ui.value(password);
            Repository.async(()->Repository.require(repo.api.login(new Models.LoginRequest(loginUser,loginPassword)).execute()),(result,error)->{
                if(isFinishing())return;
                login.setEnabled(true);
                if(error!=null) { status.setText(error);return; }
                session.save(result);password.setText("");open();
            });
        });
    }
    private void open() { startActivity(new Intent(this,HomeActivity.class));finish(); }
}
