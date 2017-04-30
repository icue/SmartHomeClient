package icue.com.smarthomeclient;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import icue.com.smarthomeclient.models.Record;
import icue.com.smarthomeclient.models.CustomPagerAdapter;

import static icue.com.smarthomeclient.models.Utils.playAudio;
import static icue.com.smarthomeclient.models.Utils.showToastMsg;

/**
 * Created by Icue on 2017/4/28.
 */

public class HistoryActivity extends AppCompatActivity {
    private Context context = this;
    private String groupID = null;

    private ViewPager mViewPager;
    private Button prevButton;
    private Button nextButton;
    private Button playButton;

    private Deque<Record> history = new ArrayDeque<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        final Bundle bundle = getIntent().getExtras();
        groupID = bundle.get("groupID").toString();
        ReadHistory();

//        ll = (LinearLayout) findViewById(R.id.historyLinear);
//
//        Iterator<Record> it = history.descendingIterator();
//        while(it.hasNext())
//        {
//            ImageView imgv = new ImageView(this);
//            try {
//                Bitmap bm = decodeFromBase64(it.next().getImage());
//                imgv.setImageBitmap(bm);
//                imgv.setVisibility(View.VISIBLE);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            ll.addView(imgv);
//        }

        PagerAdapter mCustomPagerAdapter = new CustomPagerAdapter(this, history);

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mCustomPagerAdapter);

        prevButton = (Button)findViewById(R.id.PrevButton);
        nextButton = (Button)findViewById(R.id.NextButton);
        playButton = (Button)findViewById(R.id.PlayButton);

        prevButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    prevButton.setTextColor(ContextCompat.getColor(context, R.color.white));
                    prevButton.setBackgroundResource(R.drawable.my_button_pressed);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    prevButton.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary));
                    prevButton.setBackgroundResource(R.drawable.my_button);
                    mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1, true);
                }
                return true;
            }
        });

        nextButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    nextButton.setTextColor(ContextCompat.getColor(context, R.color.white));
                    nextButton.setBackgroundResource(R.drawable.my_button_pressed);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    nextButton.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary));
                    nextButton.setBackgroundResource(R.drawable.my_button);
                    mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1, true);
                }
                return true;
            }
        });

        playButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    playButton.setTextColor(ContextCompat.getColor(context, R.color.white));
                    playButton.setBackgroundResource(R.drawable.my_button_pink_pressed);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    playButton.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
                    playButton.setBackgroundResource(R.drawable.my_button_pink);
                    playIthAudio(mViewPager.getCurrentItem());
                }
                return true;
            }
        });
    }

    private void playIthAudio(int position) {
        Iterator<Record> it = this.history.descendingIterator();
        String audio = "";
        for(int i = 0; i < position; i++)
            it.next();
        if(it.hasNext())
            audio = it.next().getAudio();
        if(audio==null || audio.equals(""))
            showToastMsg(getBaseContext(), "This record does not contain an audio.");
        else
            playAudio(audio, getCacheDir());
    }

    private void ReadHistory() {
        File file = context.getFileStreamPath(groupID);
        if (file == null || !file.exists()) {
            showToastMsg(getBaseContext(), "No history file detected.");
            return;
        }
        try {
            FileInputStream fis = context.openFileInput(groupID);
            ObjectInputStream is = new ObjectInputStream(fis);

            int count = is.readInt();
            history = new ArrayDeque<>();
            for (int c=0; c < count; c++)
                history.add((Record) is.readObject());
            showToastMsg(getBaseContext(), "History file loaded successfully. Length: " + history.size());
            is.close();
            fis.close();
        } catch (Exception e) {
            showToastMsg(getBaseContext(), "History file exists but there is read error: " + e.getMessage());
            e.printStackTrace();
        }
    }

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        unbindDrawables(ll);
//    }

//    public void unbindDrawables(View view) {
//        try {
//            if (view.getBackground() != null)
//                view.getBackground().setCallback(null);
//
//            if (view instanceof ImageView) {
//                ImageView imageView = (ImageView) view;
//                imageView.setImageBitmap(null);
//            } else if (view instanceof ViewGroup) {
//                ViewGroup viewGroup = (ViewGroup) view;
//                for (int i = 0; i < viewGroup.getChildCount(); i++)
//                    unbindDrawables(viewGroup.getChildAt(i));
//
//                if (!(view instanceof AdapterView))
//                    viewGroup.removeAllViews();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}
