package icue.com.smarthomeclient;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

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
    private NotificationManager mNotifyMgr;
    private EditText et;

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
        et = (EditText) findViewById(R.id.Message);
        FBPic = (ImageView)findViewById(R.id.FBPic);
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        myRef = database.getReference(groupID).child(applicationID);

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
                String msgToSend = et.getText().toString();
                et.setText("");
                if(!msgToSend.equals("")){
                    myRef.child("message").setValue(msgToSend);
                } else {
                    Toast.makeText(getBaseContext(), "Please fill the message.", Toast.LENGTH_SHORT).show();
                }
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
    }

    private static Bitmap decodeFromBase64(String image) throws IOException, IllegalArgumentException {
        byte[] decodedByteArray = android.util.Base64.decode(image, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.length);
    }
}
