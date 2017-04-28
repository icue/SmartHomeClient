package icue.com.smarthomeclient;

import android.app.NotificationManager;
import android.media.MediaRecorder;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
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

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
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

        Button sendButton = (Button) findViewById(R.id.SendButton);
        Button backButton = (Button) findViewById(R.id.BackButton);
        Button mIgnoreBtn = (Button) findViewById(R.id.IgnoreButton);
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
                            mRecordBtn.setText("Recording... Swap up to cancel");
                            int color = ContextCompat.getColor(context, R.color.colorAccent);
                            mRecordBtn.setBackgroundColor(color);
                        }
                    };
                    ct.start();
                    y1 = event.getY();
                    startRecord();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    long t = event.getEventTime() - event.getDownTime();
                    if(t < recordThreshold){
                        ct.cancel();
                        Toast.makeText(getBaseContext(), "time elapsed "+t, Toast.LENGTH_SHORT).show();
                        discardRecord();
                        String prevAudio = history.isEmpty() ? "" : history.getLast().getAudio();
                        playAudio(prevAudio);
                    } else {
                        y2 = event.getY();
                        dy = y2 - y1;
                        mRecordBtn.setText("Click to listen / Push to record");
                        int color = ContextCompat.getColor(context, R.color.colorPrimaryDark);
                        mRecordBtn.setBackgroundColor(color);
                        if (dy < -60){
                            discardRecord();
                            Toast.makeText(getBaseContext(), "Audio discarded.", Toast.LENGTH_SHORT).show();
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
                String msgToSend = msg.getText().toString();
                msg.setText("");
                if(!msgToSend.equals("")){
                    myRef.child("current").child("message").setValue(msgToSend);
//                    myRef.child("current").setValue(new Record("789","456"));
                } else {
                    Toast.makeText(getBaseContext(), "Please fill the message.", Toast.LENGTH_SHORT).show();
                }
                msg.clearFocus();
            }
        });

        // Read from the database
        myRef.child("current").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                String newPic = dataSnapshot.child("picture").getValue(String.class);
                if(newPic==null) return;
                prevPic = history.isEmpty() ? "" : history.getLast().getImage();

                if(!prevPic.equals(newPic)) {
//                Toast.makeText(getBaseContext(), "DB Changed " + value, Toast.LENGTH_SHORT).show();
                    history.add(new Record(newPic,
                                    dataSnapshot.child("timestamp").getValue(String.class),
                                    dataSnapshot.child("audio").getValue(String.class)
                                    ));
                    if(history.size()>10) history.removeFirst();
                    SaveHistory();

                    Toast.makeText(getBaseContext(), "DB Changed pic with timestamp " +history.getLast().getTimestamp() , Toast.LENGTH_SHORT).show();
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
                    FBPic.setImageBitmap(decodeFromBase64(newPic));
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });

//        myRef.child("current").child("audio").addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot dataSnapshot) {
//                // This method is called once with the initial value and again
//                // whenever data at this location is updated.
//                String value = dataSnapshot.getValue(String.class);
//
//                if(value!=null && !value.equals("") && !value.equals(prevAud)) {
////                    Toast.makeText(getBaseContext(), "DB Changed " + value, Toast.LENGTH_SHORT).show();
//                    try {
//                        byte[] decoded = Base64.decode(value, 0);
//
//                        MediaPlayer mediaPlayer = new MediaPlayer();
//
//                        // create temp file that will hold byte array
//                        File tempMp3 = File.createTempFile("receive_", "amr", getCacheDir());
//                        tempMp3.deleteOnExit();
//                        FileOutputStream fos = new FileOutputStream(tempMp3);
//                        fos.write(decoded);
//                        fos.close();
//
//                        // resetting mediaplayer instance to evade problems
//                        mediaPlayer.reset();
//
//                        // In case you run into issues with threading consider new instance like:
//                        // MediaPlayer mediaPlayer = new MediaPlayer();
//
//                        // Tried passing path directly, but kept getting
//                        // "Prepare failed.: status=0x1"
//                        // so using file descriptor instead
//                        FileInputStream fis = new FileInputStream(tempMp3);
//                        mediaPlayer.setDataSource(fis.getFD());
//
//                        mediaPlayer.prepare();
//                        mediaPlayer.start();
//                    } catch (Exception ex) {
//                        ex.printStackTrace();
//                    }
//                }
//                prevAud = value;
//            }
//
//            @Override
//            public void onCancelled(DatabaseError error) {
//                Log.w(TAG, "Failed to read value.", error.toException());
//            }
//        });
    }

    private static Bitmap decodeFromBase64(String image) throws IOException, IllegalArgumentException {
        byte[] decodedByteArray = android.util.Base64.decode(image, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.length);
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
                Toast.makeText(getBaseContext(), "Audio sent.", Toast.LENGTH_SHORT).show();
            } catch(RuntimeException e) {
                myAudioFile.delete();
            }
        }
    }

    private String audioEncode(File file) {
        byte[] bytes = null;
        try {
            bytes = FileUtils.readFileToByteArray(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Base64.encodeToString(bytes, 0);
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
            Toast.makeText(getBaseContext(), "save error " +e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void ReadHistory() {
        File file = context.getFileStreamPath(groupID);
        if (file == null || !file.exists()) {
            Toast.makeText(getBaseContext(), "no file exist", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            FileInputStream fis = context.openFileInput(groupID);
            ObjectInputStream is = new ObjectInputStream(fis);

            int count = is.readInt();
            history = new ArrayDeque<>();
            for (int c=0; c < count; c++)
                history.add((Record) is.readObject());

            Toast.makeText(getBaseContext(), "Newest timestamp " +history.getLast().getTimestamp(), Toast.LENGTH_SHORT).show();
            is.close();
            fis.close();
        } catch (Exception e) {
            Toast.makeText(getBaseContext(), "read error " +e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void playAudio(String audio) {
        try {
            byte[] decoded = Base64.decode(audio, 0);
            MediaPlayer mediaPlayer = new MediaPlayer();
            File tempMp3 = File.createTempFile("audio_", "amr", getCacheDir());
            tempMp3.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempMp3);
            fos.write(decoded);
            fos.close();
            mediaPlayer.reset();
            FileInputStream fis = new FileInputStream(tempMp3);
            mediaPlayer.setDataSource(fis.getFD());
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
