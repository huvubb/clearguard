package com.youlong.hd;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
/** Shared native surfaces for the tool screens. */
final class NativeUi {
 private static final int INK=0xFF173C30,MUTED=0xFF526358,BG=0xFFF5F7F2;
 private NativeUi(){}
 static void insets(Activity activity,View root){WindowCompat.setDecorFitsSystemWindows(activity.getWindow(),false);activity.getWindow().setStatusBarColor(Color.TRANSPARENT);activity.getWindow().setNavigationBarColor(BG);activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});ViewCompat.requestApplyInsets(root);}
 static void polish(Activity activity){ViewGroup decor=activity.findViewById(android.R.id.content);if(decor==null||decor.getChildCount()==0)return;View root=decor.getChildAt(0);root.setBackgroundColor(BG);if(root instanceof ScrollView&&((ScrollView)root).getChildCount()>0)((ScrollView)root).getChildAt(0).setPadding(dp(activity,24),dp(activity,20),dp(activity,24),dp(activity,24));insets(activity,root);walk(activity,root);}
 private static int dp(Activity a,int n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
 private static GradientDrawable shape(Activity a,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;}
 private static void walk(Activity a,View view){
  if(view instanceof CheckBox){CheckBox c=(CheckBox)view;c.setTextColor(INK);c.setTextSize(14);c.setMinimumHeight(dp(a,48));c.setButtonTintList(ColorStateList.valueOf(0xFF356847));c.setBackgroundColor(Color.TRANSPARENT);}
  else if(view instanceof Spinner){view.setBackground(shape(a,0xFFF0F4EC,16));view.setMinimumHeight(dp(a,50));view.setPadding(dp(a,8),dp(a,4),dp(a,8),dp(a,4));}
  else if(view instanceof Button){Button b=(Button)view;String text=b.getText().toString();boolean primary="cg-primary".equals(b.getTag())||(!"cg-secondary".equals(b.getTag())&&text.contains("开启"))||text.contains("执行")||text.contains("启动")||text.contains("扫描已");GradientDrawable surface=shape(a,primary?INK:Color.WHITE,26);if(!primary)surface.setStroke(dp(a,1),0xFFDCE5D8);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2252785A),surface,null));b.setTextColor(primary?Color.WHITE:INK);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinHeight(dp(a,52));b.setMinimumHeight(dp(a,52));b.setPadding(dp(a,14),dp(a,12),dp(a,14),dp(a,12));b.setElevation(0);ViewGroup.LayoutParams p=b.getLayoutParams();if(p instanceof ViewGroup.MarginLayoutParams){p.height=ViewGroup.LayoutParams.WRAP_CONTENT;((ViewGroup.MarginLayoutParams)p).bottomMargin=dp(a,10);b.setLayoutParams(p);}}
  else if(view instanceof EditText){EditText input=(EditText)view;GradientDrawable d=shape(a,Color.WHITE,18);d.setStroke(dp(a,1),0xFFDCE5D8);input.setBackground(d);input.setPadding(dp(a,16),dp(a,14),dp(a,16),dp(a,14));input.setTextColor(INK);input.setHintTextColor(MUTED);input.setTextSize(14);input.setMinimumHeight(dp(a,52));}
  else if(view instanceof TextView){TextView t=(TextView)view;if("cg-output".equals(t.getTag()))t.setTextColor(0xFFE2EFD9);else if("cg-label".equals(t.getTag())){}else t.setTextColor(INK);t.setLineSpacing(dp(a,3),1.05f);if(t.getTextSize()/a.getResources().getDisplayMetrics().scaledDensity>=22)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)walk(a,group.getChildAt(i));}
 }
 static final class Page {
  final Activity activity;final LinearLayout root,body;private final String title,description;
  Page(Activity a,String title,String subtitle,String description){this.activity=a;this.title=title;this.description=description;root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(a,24),dp(a,12),dp(a,24),dp(a,24));
   LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(a,20),dp(a,14),dp(a,20),dp(a,10));
   TextView back=label(a,"‹",30,INK);back.setGravity(Gravity.CENTER);back.setBackground(shape(a,Color.WHITE,22));back.setContentDescription("返回");back.setOnClickListener(v->a.finish());header.addView(back,new LinearLayout.LayoutParams(dp(a,44),dp(a,44)));
   TextView brand=label(a,"CLEAR GUARD",10,MUTED);brand.setPadding(dp(a,14),0,0,0);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));TextView info=label(a,"使用说明",12,INK);info.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));info.setOnClickListener(v->new android.app.AlertDialog.Builder(a).setTitle(title+" · 使用说明").setMessage(description).setPositiveButton("知道了",null).show());header.addView(info);root.addView(header);
   TextView heading=label(a,title,28,INK);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));body.addView(heading);TextView summary=label(a,subtitle,13,MUTED);summary.setPadding(0,dp(a,8),0,dp(a,24));body.addView(summary);
   ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  }
  LinearLayout section(String title){TextView label=label(activity,title,13,MUTED);label.setPadding(0,dp(activity,8),0,dp(activity,10));body.addView(label);LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(activity,18),dp(activity,18),dp(activity,18),dp(activity,12));card.setBackground(shape(activity,Color.WHITE,26));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(activity,18);body.addView(card,lp);return card;}
  void show(){activity.setContentView(root);insets(activity,root);walk(activity,body);}
 }
 static TextView label(Activity a,String text,int size,int color){TextView t=new TextView(a);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setTag("cg-label");return t;}
 static void field(Activity a,LinearLayout parent,String title,View input){TextView label=label(a,title,12,MUTED);label.setPadding(0,dp(a,4),0,dp(a,8));parent.addView(label);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(a,14);parent.addView(input,lp);}
 static Button action(Activity a,LinearLayout parent,String text,Runnable work,boolean primary){Button b=new Button(a);b.setText(text);b.setTag(primary?"cg-primary":"cg-secondary");parent.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->work.run());return b;}
 static void report(Activity a,LinearLayout parent,TextView text,boolean dark){text.setTextIsSelectable(true);text.setTag(dark?"cg-output":"cg-status");text.setTextSize(13);text.setPadding(dp(a,16),dp(a,18),dp(a,16),dp(a,18));text.setBackground(shape(a,dark?INK:0xFFEAF0E3,20));parent.addView(text,new LinearLayout.LayoutParams(-1,-2));}
 static void legacy(Activity a,String title,String subtitle){ViewGroup decor=a.findViewById(android.R.id.content);if(decor==null||decor.getChildCount()==0)return;View original=decor.getChildAt(0);decor.removeView(original);View inner=original instanceof ScrollView?((ScrollView)original).getChildAt(0):original;StringBuilder help=new StringBuilder();
  if(inner instanceof LinearLayout){LinearLayout column=(LinearLayout)inner;if(column.getChildCount()>0&&column.getChildAt(0) instanceof TextView&&!(column.getChildAt(0) instanceof Button))column.removeViewAt(0);for(int i=0;i<column.getChildCount();i++){View item=column.getChildAt(i);if(item instanceof TextView&&!(item instanceof Button)&&((TextView)item).getText().length()>90){help.append(((TextView)item).getText()).append("\n\n");column.removeViewAt(i--);}}column.setPadding(0,0,0,dp(a,20));}
  Page p=new Page(a,title,subtitle,help.length()>0?help.toString():"所有操作均在设备本机执行；涉及系统权限的操作会要求单独授权。");
  if(original instanceof ScrollView){((ScrollView)original).removeView(inner);p.body.addView(inner,new LinearLayout.LayoutParams(-1,-2));}else{View scroll=p.root.getChildAt(p.root.getChildCount()-1);p.root.removeView(scroll);((ScrollView)scroll).removeView(p.body);p.root.addView(p.body);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1);lp.setMargins(dp(a,24),0,dp(a,24),0);p.root.addView(original,lp);}
  p.show();walk(a,inner);
 }

}
