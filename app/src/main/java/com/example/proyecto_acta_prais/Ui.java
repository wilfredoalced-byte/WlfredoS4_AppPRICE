package com.example.proyecto_acta_prais;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.CompoundButtonCompat;

public final class Ui {
    public static int dp(Context c,int value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    public static LinearLayout screen(AppCompatActivity a,String title,String subtitle) {
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setBackgroundColor(ContextCompat.getColor(a,R.color.surface));
        LinearLayout body=column(a);body.setPadding(dp(a,16),dp(a,16),dp(a,16),dp(a,30));scroll.addView(body);a.setContentView(scroll);insets(scroll);

        LinearLayout header=column(a);header.setBackgroundResource(R.drawable.bg_header);header.setPadding(dp(a,20),dp(a,18),dp(a,20),dp(a,18));header.setElevation(dp(a,2));
        body.addView(header,new LinearLayout.LayoutParams(-1,-2));
        TextView org=new TextView(a);org.setText("OSINERGMIN");org.setTextSize(12);org.setTypeface(null,Typeface.BOLD);org.setTextColor(ContextCompat.getColor(a,R.color.accent_700));org.setLetterSpacing(0.14f);header.addView(org);
        TextView heading=new TextView(a);heading.setText(title);heading.setTextSize(24);heading.setTypeface(null,Typeface.BOLD);heading.setTextColor(ContextCompat.getColor(a,R.color.white));heading.setLetterSpacing(0.01f);heading.setPadding(0,dp(a,2),0,0);header.addView(heading);
        if(!subtitle.isEmpty()) { TextView sub=new TextView(a);sub.setText(subtitle);sub.setTextSize(13);sub.setTextColor(ContextCompat.getColor(a,R.color.blue_100));sub.setPadding(0,dp(a,4),0,0);header.addView(sub); }
        return body;
    }
    public static void infoBox(LinearLayout parent,String value) {
        Context c=parent.getContext();
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.HORIZONTAL);box.setBackgroundResource(R.drawable.bg_info_box);box.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));box.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView dot=new TextView(c);dot.setText("i");dot.setTypeface(null,Typeface.BOLD);dot.setTextColor(ContextCompat.getColor(c,R.color.white));dot.setTextSize(11);dot.setGravity(android.view.Gravity.CENTER);dot.setBackgroundResource(R.drawable.bg_info_dot);
        LinearLayout.LayoutParams dotLp=new LinearLayout.LayoutParams(dp(c,20),dp(c,20));dotLp.rightMargin=dp(c,10);box.addView(dot,dotLp);
        TextView text=new TextView(c);text.setText(value);text.setTextSize(12.5f);text.setTextColor(ContextCompat.getColor(c,R.color.navy_900));box.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,10);parent.addView(box,lp);
    }
    public static void menuButton(LinearLayout parent,int iconRes,String label,View.OnClickListener listener) {
        Context c=parent.getContext();
        LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setBackgroundResource(R.drawable.bg_menu_item);row.setPadding(dp(c,14),dp(c,14),dp(c,16),dp(c,14));row.setElevation(dp(c,2));row.setOnClickListener(listener);
        ImageView icon=new ImageView(c);icon.setImageResource(iconRes);icon.setBackgroundResource(R.drawable.bg_icon_chip);icon.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));
        LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(c,44),dp(c,44));iconLp.rightMargin=dp(c,14);row.addView(icon,iconLp);
        TextView lbl=new TextView(c);lbl.setText(label);lbl.setTextSize(15);lbl.setTypeface(null,Typeface.BOLD);lbl.setTextColor(ContextCompat.getColor(c,R.color.slate_900));row.addView(lbl,new LinearLayout.LayoutParams(0,-2,1));
        TextView chevron=new TextView(c);chevron.setText("›");chevron.setTextSize(20);chevron.setTextColor(ContextCompat.getColor(c,R.color.slate_500));row.addView(chevron);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,10);parent.addView(row,lp);
    }
    public static void insets(View view) {
        int l=view.getPaddingLeft(),t=view.getPaddingTop(),r=view.getPaddingRight(),b=view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view,(v,i)-> { Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());v.setPadding(l+s.left,t+s.top,r+s.right,b+s.bottom);return i; });
        ViewCompat.requestApplyInsets(view);
    }
    public static LinearLayout column(Context c) { LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);v.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return v; }
    public static TextView text(LinearLayout parent,String value,int size) {
        TextView v=new TextView(parent.getContext());v.setText(value);v.setTextSize(size);v.setTextColor(ContextCompat.getColor(parent.getContext(),R.color.slate_700));v.setPadding(0,dp(parent.getContext(),7),0,dp(parent.getContext(),7));parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private static EditText input(Context c,LinearLayout parent,String value,int type) {
        EditText v=new EditText(c);v.setSingleLine(true);v.setTextSize(15);v.setInputType(type);v.setText(value);v.setMinHeight(dp(c,50));v.setBackgroundResource(R.drawable.bg_input);v.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    public static EditText field(LinearLayout parent,String label,String value,int input) {
        Context c=parent.getContext();
        TextView lbl=new TextView(c);lbl.setText(label);lbl.setTextSize(12);lbl.setTypeface(null,Typeface.BOLD);lbl.setTextColor(ContextCompat.getColor(c,R.color.slate_700));lbl.setLetterSpacing(0.01f);lbl.setPadding(0,dp(c,10),0,dp(c,3));parent.addView(lbl,new LinearLayout.LayoutParams(-1,-2));
        return input(c,parent,value,input);
    }
    public static EditText field(LinearLayout parent,int iconRes,String label,String value,int input) {
        Context c=parent.getContext();
        LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(c);icon.setImageResource(iconRes);
        LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(c,16),dp(c,16));iconLp.rightMargin=dp(c,6);row.addView(icon,iconLp);
        TextView lbl=new TextView(c);lbl.setText(label);lbl.setTextSize(12);lbl.setTypeface(null,Typeface.BOLD);lbl.setTextColor(ContextCompat.getColor(c,R.color.slate_700));lbl.setLetterSpacing(0.01f);row.addView(lbl);
        LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.topMargin=dp(c,10);rowLp.bottomMargin=dp(c,3);parent.addView(row,rowLp);
        return input(c,parent,value,input);
    }
    public static AutoCompleteTextView autocomplete(LinearLayout parent,String label,String value,java.util.List<String> suggestions) {
        Context c=parent.getContext();
        TextView lbl=new TextView(c);lbl.setText(label);lbl.setTextSize(12);lbl.setTypeface(null,Typeface.BOLD);lbl.setTextColor(ContextCompat.getColor(c,R.color.slate_700));lbl.setLetterSpacing(0.01f);lbl.setPadding(0,dp(c,10),0,dp(c,3));parent.addView(lbl,new LinearLayout.LayoutParams(-1,-2));
        AutoCompleteTextView v=new AutoCompleteTextView(c);v.setSingleLine(true);v.setTextSize(15);v.setText(value);v.setMinHeight(dp(c,50));v.setBackgroundResource(R.drawable.bg_input);v.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));v.setThreshold(1);v.setDropDownHeight(dp(c,220));
        v.setAdapter(new ArrayAdapter<>(c,android.R.layout.simple_dropdown_item_1line,suggestions));
        parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    public static Button button(LinearLayout parent,String label,View.OnClickListener listener) {
        Context c=parent.getContext();
        Button b=new Button(c);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(c,52));
        b.setBackgroundResource(R.drawable.bg_button);b.setTextColor(ContextCompat.getColor(c,R.color.white));
        b.setPadding(dp(c,18),dp(c,12),dp(c,18),dp(c,12));b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,8);parent.addView(b,lp);return b;
    }
    public static void tint(CompoundButton cb) {
        CompoundButtonCompat.setButtonTintList(cb,ColorStateList.valueOf(ContextCompat.getColor(cb.getContext(),R.color.navy_700)));
    }
    public static LinearLayout card(LinearLayout parent) {
        LinearLayout c=column(parent.getContext());c.setBackgroundResource(R.drawable.bg_card);c.setPadding(dp(parent.getContext(),16),dp(parent.getContext(),14),dp(parent.getContext(),16),dp(parent.getContext(),14));c.setElevation(dp(parent.getContext(),3));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(parent.getContext(),12);parent.addView(c,lp);return c;
    }
    public static void message(Context c,String value) { new AlertDialog.Builder(c).setTitle("PRICE").setMessage(value).setPositiveButton("Aceptar",null).show(); }
    public static String value(EditText v) { return v.getText().toString().trim(); }
}
