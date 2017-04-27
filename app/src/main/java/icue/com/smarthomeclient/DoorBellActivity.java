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
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import icue.com.smarthomeclient.models.InstantAutoComplete;

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

/**
 * Created by Icue on 2017/4/25.
 */

public class DoorBellActivity extends AppCompatActivity {

    private static final String TAG = "DoorBellActivity";

    private static String androidID;
    private static String applicationID = "1";
    private String groupID = null;
    private DatabaseReference myRef;
    private ImageView FBPic;
    private Context context = this;
    private String prevPic = null;
    private String prevAud = "";
    private NotificationManager mNotifyMgr;
    private InstantAutoComplete msg;
    private Button mRecordBtn;
    private MediaRecorder mediaRecorder = new MediaRecorder();
    private File mAudioFile;

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
        Button sendButton = (Button) findViewById(R.id.SendButton);
        Button backButton = (Button) findViewById(R.id.BackButton);
        mRecordBtn = (Button) findViewById(R.id.RecordButton);
//        et = (EditText) findViewById(R.id.Message);

        FBPic = (ImageView)findViewById(R.id.FBPic);
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        myRef = database.getReference(groupID).child(applicationID);

        String[] responseTemplates ={"Please come in.",
                                    "I'm not home.",
                                    "Please don't wait for me.",
                                    "Be right back soon."};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,responseTemplates);
        //Getting the instance of AutoCompleteTextView
        msg = (InstantAutoComplete) findViewById(R.id.Message);
        msg.setThreshold(0);//will start working from first character
        msg.setAdapter(adapter);//setting the adapter data into the AutoCompleteTextView

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
                    y1 = event.getY();
                    mRecordBtn.setText("Recording... Swap up to cancel.");
                    int color = ContextCompat.getColor(context, R.color.colorAccent);
                    mRecordBtn.setBackgroundColor(color);
                    startRecord();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    y2 = event.getY();
                    dy = y2 - y1;
                    mRecordBtn.setText("Push to record audio.");
                    mRecordBtn.setBackgroundResource(android.R.drawable.btn_default);
                    if(dy < -60)
                        discardRecord();
                    else
                        stopRecord();
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
                    myRef.child("message").setValue(msgToSend);
                } else {
                    Toast.makeText(getBaseContext(), "Please fill the message.", Toast.LENGTH_SHORT).show();
                }
                msg.clearFocus();
            }
        });

        // Read from the database
        myRef.child("picture").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                String value = dataSnapshot.getValue(String.class);
                if(value==null) return;

                // value should not start with "data:image/jpeg;base64,/9j/...
//                Log.d(TAG, "Value is: " + value);
//                Toast.makeText(getBaseContext(), "DB Changed " + value, Toast.LENGTH_SHORT).show();
                try {
                    FBPic.setImageBitmap(decodeFromBase64(value));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if(prevPic != null && !prevPic.equals(value)) {
                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("New activity")
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
                prevPic = value;
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });

        myRef.child("audio").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                String value = dataSnapshot.getValue(String.class);

                if(value!=null && !value.equals("") && !value.equals(prevAud)) {
//                    Toast.makeText(getBaseContext(), "DB Changed " + value, Toast.LENGTH_SHORT).show();
                    try {
                        byte[] decoded = Base64.decode(value, 0);

                        MediaPlayer mediaPlayer = new MediaPlayer();

                        // create temp file that will hold byte array
                        File tempMp3 = File.createTempFile("receive_", "amr", getCacheDir());
                        tempMp3.deleteOnExit();
                        FileOutputStream fos = new FileOutputStream(tempMp3);
                        fos.write(decoded);
                        fos.close();

                        // resetting mediaplayer instance to evade problems
                        mediaPlayer.reset();

                        // In case you run into issues with threading consider new instance like:
                        // MediaPlayer mediaPlayer = new MediaPlayer();

                        // Tried passing path directly, but kept getting
                        // "Prepare failed.: status=0x1"
                        // so using file descriptor instead
                        FileInputStream fis = new FileInputStream(tempMp3);
                        mediaPlayer.setDataSource(fis.getFD());

                        mediaPlayer.prepare();
                        mediaPlayer.start();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                prevAud = value;
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
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
                mAudioFile = File.createTempFile("send_", ".amr", getCacheDir());
                mAudioFile.deleteOnExit();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void discardRecord() {
        if (mAudioFile != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Toast.makeText(getBaseContext(), "Audio discarded.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecord() {
        if (mAudioFile != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            myRef.child("clientAudio").setValue(audioEncode(mAudioFile));
            Toast.makeText(getBaseContext(), "Audio sent.", Toast.LENGTH_SHORT).show();
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
}
