package icue.com.smarthomeclient;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static String androidID;
    private String groupID = null;

    private Button proceedButton;
    private TextView instTextView;
    private ImageView QRCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        // Make full screen
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        androidID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        instTextView = (TextView) findViewById(R.id.instIDTextView);
        Button scanButton = (Button) findViewById(R.id.scanButton);
        proceedButton = (Button) findViewById(R.id.proceedButton);
        QRCode =  (ImageView)findViewById(R.id.QRCode);

        if(!fileExists(this,"myGroupID.txt")){
            proceedButton.setEnabled(false);
        }
        else {  // prepare to start the doorbell activity directly
            groupID = ReadID(instTextView);

            if(!getIntent().hasExtra("wantBack")){  // if the client clicked back to get here, stay
                Intent intent = new Intent(MainActivity.this, DoorBellActivity.class);
                intent.putExtra("groupID", groupID);
                intent.putExtra("androidID", androidID);
                startActivity(intent);
                finish();
            }

            instTextView.setText(groupID);
            try {
                Bitmap bitmap = TextToImageEncode(groupID);
                QRCode.setImageBitmap(bitmap);
            } catch (WriterException e) {
                e.printStackTrace();
            }
        }

        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //Propagate Main Activity to IntentIntegrator
                IntentIntegrator intentIntegrator = new IntentIntegrator(MainActivity.this);
                intentIntegrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
                intentIntegrator.setPrompt("Scan");
                intentIntegrator.setCameraId(0);
                intentIntegrator.setBeepEnabled(false);
                intentIntegrator.setBarcodeImageEnabled(false);
                intentIntegrator.initiateScan();
            }
        });

        proceedButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DoorBellActivity.class);
                intent.putExtra("groupID", groupID);
                intent.putExtra("androidID", androidID);
                startActivity(intent);
                finish();
            }
        });
    }

    /**
     * QR code scan function
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);

        if (result != null) {
            if (result.getContents() == null) {
                Log.d("Main Activity Scan", "Cancel Scan ====================================");
                Toast.makeText(this, "Canceled", Toast.LENGTH_LONG).show();
            } else {
                // get result from Zxing's lib
                Log.d("Main Activity Scan", "Scanned ====================================");
                // start google search activity immediately
                System.out.println("result: " + result.getContents());

                groupID = result.getContents();
                System.out.println("URL: " + groupID);
                instTextView.setText(groupID);
                if(!groupID.isEmpty()) {
                    WriteID(instTextView);
                    proceedButton.setEnabled(true);
                    try {
                        Bitmap bitmap = TextToImageEncode(groupID);
                        QRCode.setImageBitmap(bitmap);
                    } catch (WriterException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            // Repeatedly call
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    Bitmap TextToImageEncode(String Value) throws WriterException {
        BitMatrix bitMatrix;
        try {
            bitMatrix = new MultiFormatWriter().encode(
                Value,
                BarcodeFormat.DATA_MATRIX.QR_CODE,
                1000, 1000, null
            );
        } catch (IllegalArgumentException Illegalargumentexception) {
            return null;
        }
        int bitMatrixWidth = bitMatrix.getWidth();
        int bitMatrixHeight = bitMatrix.getHeight();
        int[] pixels = new int[bitMatrixWidth * bitMatrixHeight];
            for (int y = 0; y < bitMatrixHeight; y++) {
            int offset = y * bitMatrixWidth;
            for (int x = 0; x < bitMatrixWidth; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ?
                        Color.rgb(0,0,0):Color.rgb(255,255,255);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(bitMatrixWidth, bitMatrixHeight, Bitmap.Config.ARGB_4444);
        bitmap.setPixels(pixels, 0, 1000, 0, 0, bitMatrixWidth, bitMatrixHeight);
        return bitmap;
    }


    public boolean fileExists(Context context, String filename) {
        File file = context.getFileStreamPath(filename);
        return file != null && file.exists();
    }

    // write text to file
    public void WriteID(TextView v) {
        try {
            FileOutputStream fileOut = openFileOutput("myGroupID.txt", MODE_PRIVATE);
            OutputStreamWriter outputWriter = new OutputStreamWriter(fileOut);
            outputWriter.write(v.getText().toString());
            outputWriter.close();
            //display file saved message
            Toast.makeText(getBaseContext(), "File saved successfully!",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Read text from file
    public String ReadID(TextView v) {
        try {
            FileInputStream fileIn = openFileInput("myGroupID.txt");
            InputStreamReader inputStreamReader = new InputStreamReader(fileIn);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null)
                sb.append(line);
            inputStreamReader.close();
            v.setText(sb);
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
