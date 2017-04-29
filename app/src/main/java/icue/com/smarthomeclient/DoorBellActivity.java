package icue.com.smarthomeclient;

import android.app.NotificationManager;
import android.media.MediaRecorder;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import icue.com.smarthomeclient.models.InstantAutoComplete;
import icue.com.smarthomeclient.models.Record;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;

import static icue.com.smarthomeclient.models.Utils.audioEncode;
import static icue.com.smarthomeclient.models.Utils.decodeFromBase64;
import static icue.com.smarthomeclient.models.Utils.playAudio;
import static icue.com.smarthomeclient.models.Utils.showToastMsg;

/**
 * Created by Icue on 2017/4/25.
 */

public class DoorBellActivity extends AppCompatActivity {

    private static final String TAG = "DoorBellActivity";

    private static String androidID;
    private static String applicationID = "1";
    private static int recordThreshold = 400;
    private static String[] responseTemplates ={"Please come in.",
                                                "I'm not home.",
                                                "Please don't wait for me.",
                                                "Be right back soon."};
    private Context context = this;
    private String groupID = null;
    private DatabaseReference myRef;
    private ImageView FBPic;
    private String prevPic = "";
    private NotificationManager mNotifyMgr;
    private InstantAutoComplete msg;
    private Button mRecordBtn;
    private MediaRecorder mediaRecorder = new MediaRecorder();
    private File myAudioFile;
    private CountDownTimer ct;
    private boolean canSendMsg = true;

    private Deque<Record> history = new ArrayDeque<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        // Make full screen
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_doorbell);
        mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.cancelAll();

        final Bundle bundle = getIntent().getExtras();
        groupID = bundle.get("groupID").toString();
        androidID = bundle.get("androidID").toString();

        ReadHistory();

        final Button sendButton = (Button) findViewById(R.id.SendButton);
        Button backButton = (Button) findViewById(R.id.BackButton);
        Button mIgnoreBtn = (Button) findViewById(R.id.IgnoreButton);
        final Button historyBtn = (Button) findViewById(R.id.HistoryButton);
        mRecordBtn = (Button) findViewById(R.id.RecordButton);
        FBPic = (ImageView)findViewById(R.id.FBPic);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        myRef = database.getReference(groupID).child(applicationID);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,responseTemplates);
        msg = (InstantAutoComplete) findViewById(R.id.Message);
        msg.setThreshold(0);
        msg.setAdapter(adapter);
        msg.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                msg.showDropDown();
            }
        });

        mRecordBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float y1 = 0, y2, dy;
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    ct = new CountDownTimer(recordThreshold, recordThreshold) {
                        @Override
                        public void onTick(long arg0) {
                            // Auto-generated method stub
                        }
                        @Override
                        public void onFinish() {
                            if(canSendMsg) {
                                mRecordBtn.setText("Recording... Swap up to cancel");
                                int color = ContextCompat.getColor(context, R.color.colorAccent);
                                mRecordBtn.setBackgroundColor(color);
                            } else {
                                mRecordBtn.setText("Can't send message now.");
                                int color = ContextCompat.getColor(context, R.color.disabled);
                                mRecordBtn.setBackgroundColor(color);
                            }
                        }
                    };
                    ct.start();
                    y1 = event.getY();
                    startRecord();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    long t = event.getEventTime() - event.getDownTime();
                    if(t < recordThreshold){
                        ct.cancel();
//                        Toast.makeText(getBaseContext(), "time elapsed "+t, Toast.LENGTH_SHORT).show();
                        discardRecord();
                        String prevAudio = history.isEmpty() ? "" : history.getLast().getAudio();
                        playAudio(prevAudio, getCacheDir());
                    } else {
                        y2 = event.getY();
                        dy = y2 - y1;
                        int color = ContextCompat.getColor(context, R.color.colorPrimaryDark);
                        mRecordBtn.setBackgroundColor(color);
                        if(!canSendMsg) {
                            showToastMsg(getBaseContext(), "Can't send message now because a response has been sent by you or your family member.", 1);
                            mRecordBtn.setText("Click to play");
                        }
                        else
                            mRecordBtn.setText("Click to play / Push to record");
                        if (dy < -60 || !canSendMsg){
                            discardRecord();
                            showToastMsg(getBaseContext(), "Audio discarded");
                        }
                        else
                            stopRecord();
                    }
                }
                return true;
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(DoorBellActivity.this, MainActivity.class);
                intent.putExtra("wantBack", "YES");
                startActivity(intent);
                finish();
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(!canSendMsg) {
                    showToastMsg(getBaseContext(), "Can't send message now because a response has been sent by you or your family member.", 1);
                    return;
                }
                String msgToSend = msg.getText().toString();
                msg.setText("");
                if(!msgToSend.equals("")){
                    myRef.child("current").child("message").setValue(msgToSend);
                } else {
                    showToastMsg(getBaseContext(), "Please fill the message.");
                }
                msg.clearFocus();
            }
        });

        historyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(DoorBellActivity.this, HistoryActivity.class);
                intent.putExtra("groupID", groupID);
                startActivity(intent);
            }
        });

        // Read from the database
        myRef.child("current").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                String msg = dataSnapshot.child("message").getValue(String.class);
                canSendMsg = msg.equals("");
                if(canSendMsg) {
                    sendButton.setBackgroundResource(R.drawable.my_button);
                    int color = ContextCompat.getColor(context, R.color.colorPrimary);
                    sendButton.setTextColor(color);
                    mRecordBtn.setText("Click to play / Push to record");
                }
                else{
                    sendButton.setBackgroundResource(R.drawable.my_button_disabled);
                    int color = ContextCompat.getColor(context, R.color.disabled);
                    sendButton.setTextColor(color);
                    mRecordBtn.setText("Click to play");
                }

                String newPic = dataSnapshot.child("picture").getValue(String.class);
                if(newPic==null) return;
                prevPic = history.isEmpty() ? "" : history.getLast().getImage();

                if(!prevPic.equals(newPic)) {
                    history.add(new Record(newPic,
                                    dataSnapshot.child("timestamp").getValue(String.class),
                                    dataSnapshot.child("audio").getValue(String.class)
                                    ));
                    if(history.size()>10) history.removeFirst();
                    SaveHistory();

                    showToastMsg(getBaseContext(), "DB updated picture with timestamp " + history.getLast().getTimestamp());
                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("New visitor")
                            .setContentText("A new photo has been received.")
                            .setAutoCancel(true);

                    Intent resultIntent = new Intent(context, MainActivity.class);
                    PendingIntent resultPendingIntent = PendingIntent.getActivity(
                            context,
                            0,
                            resultIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );

                    mBuilder.setContentIntent(resultPendingIntent);
                    mNotifyMgr.notify(1, mBuilder.build());
                }

                // newPic should not start with "data:image/jpeg;base64,/9j/...
                try {
                    Bitmap bm = decodeFromBase64(newPic);
                    FBPic.setImageBitmap(bm);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    private void startRecord() {
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
            try {
                myAudioFile = File.createTempFile("send_", ".amr", getCacheDir());
                myAudioFile.deleteOnExit();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaRecorder.setOutputFile(myAudioFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void discardRecord() {
        if (myAudioFile != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            } catch(RuntimeException e) {
                myAudioFile.delete();
            }
        }
    }

    private void stopRecord() {
        if (myAudioFile != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                myRef.child("current").child("clientAudio").setValue(audioEncode(myAudioFile));
                showToastMsg(getBaseContext(), "Audio sent.");
            } catch(RuntimeException e) {
                myAudioFile.delete();
            }
        }
    }

    private void SaveHistory() {
        try {
            FileOutputStream fos = context.openFileOutput(groupID, MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);

            os.writeInt(history.size()); // Save size first

            for(Record r : history)
                os.writeObject(r);

            os.close();
            fos.close();
        } catch (Exception e) {
            showToastMsg(getBaseContext(), "History file save error: " + e.getMessage());
            e.printStackTrace();
        }
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


}
