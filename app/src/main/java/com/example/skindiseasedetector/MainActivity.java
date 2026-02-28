package com.example.skindiseasedetector;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_GALLERY = 100;
    private static final int REQUEST_CAMERA = 101;
    private static final int PERMISSION_REQUESTS = 102;

    private ImageView imageView;
    private TextView tvResult;
    private Bitmap selectedBitmap;
    private Interpreter tflite;
    private String currentPhotoPath;
    private List<String> labels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        tvResult = findViewById(R.id.tvResult);
        Button btnPick = findViewById(R.id.btnPick);
        Button btnCapture = findViewById(R.id.btnCapture);
        Button btnPredict = findViewById(R.id.btnPredict);
        Button btnFindDerm = findViewById(R.id.btnFindDerm);

        requestPermissionsIfNeeded();

        try {
            tflite = new Interpreter(loadModelFile(this, "model.tflite"));
            labels = loadLabels(this, "labels.txt");
            Toast.makeText(this, "Model loaded successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Model loading failed", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        btnPick.setOnClickListener(v -> pickImageFromGallery());
        btnCapture.setOnClickListener(v -> captureImage());
        btnPredict.setOnClickListener(v -> {
            if (selectedBitmap == null) {
                Toast.makeText(this, "Select or capture an image first", Toast.LENGTH_SHORT).show();
            } else {
                runModel(selectedBitmap);
            }
        });
        btnFindDerm.setOnClickListener(v -> startActivity(new Intent(this, NearbyDermatologistsActivity.class)));
    }

    private void requestPermissionsIfNeeded() {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.CAMERA);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (!permissions.isEmpty())
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUESTS);
    }

    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    private void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                File photoFile = createImageFile();
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                startActivityForResult(intent, REQUEST_CAMERA);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private File createImageFile() throws IOException {
        String fileName = "IMG_" + System.currentTimeMillis();
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(fileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;

        try {
            if (requestCode == REQUEST_GALLERY && data != null) {
                Uri uri = data.getData();
                selectedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            } else if (requestCode == REQUEST_CAMERA) {
                selectedBitmap = BitmapFactory.decodeFile(currentPhotoPath);
            }
            imageView.setImageBitmap(selectedBitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runModel(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
        ByteBuffer input = convertBitmapToByteBuffer(resized);
        float[][] output = new float[1][labels.size()];
        tflite.run(input, output);

        int maxIndex = 0;
        float maxProb = 0;
        for (int i = 0; i < output[0].length; i++) {
            if (output[0][i] > maxProb) {
                maxProb = output[0][i];
                maxIndex = i;
            }
        }

        String label = labels.get(maxIndex);
        float confidence = maxProb * 100;

        boolean isCancer = label.toLowerCase().contains("melanoma") ||
                label.toLowerCase().contains("basal") ||
                label.toLowerCase().contains("carcinoma") ||
                label.toLowerCase().contains("bowen");

        String[] rec = getRecommendations(label);

        // 🔥 Styled colored output
        String cancerText = isCancer
                ? "<b>Cancer:</b> <font color='#DC2626'>YES ⚠️</font>"
                : "<b>Cancer:</b> <font color='#16A34A'>NO ✅</font>";

        String resultText = "<b>Prediction:</b> " + label + " (" + String.format("%.2f", confidence) + "%)<br><br>"
                + cancerText + "<br><br>"
                + "<b>Eat:</b> " + rec[0] + "<br>"
                + "<b>Avoid:</b> " + rec[1] + "<br>"
                + "<b>Advice:</b> " + rec[2];

        tvResult.setText(Html.fromHtml(resultText));
        findViewById(R.id.resultLayout).setVisibility(View.VISIBLE);
    }

    private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }

    private List<String> loadLabels(Context context, String fileName) throws IOException {
        List<String> labels = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(fileName)));
        String line;
        while ((line = reader.readLine()) != null) labels.add(line);
        reader.close();
        return labels;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[224 * 224];
        bitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224);
        for (int pixelValue : intValues) {
            byteBuffer.putFloat(((pixelValue >> 16) & 0xFF) / 255.f);
            byteBuffer.putFloat(((pixelValue >> 8) & 0xFF) / 255.f);
            byteBuffer.putFloat((pixelValue & 0xFF) / 255.f);
        }
        return byteBuffer;
    }

    private String[] getRecommendations(String label) {
        label = label.toLowerCase();
        if (label.contains("ringworm"))
            return new String[]{"Garlic, turmeric", "Oily foods", "Use antifungal cream"};
        if (label.contains("melanoma"))
            return new String[]{"Vitamin D, fruits", "Alcohol", "Visit oncologist"};
        if (label.contains("basal"))
            return new String[]{"Antioxidant foods", "Sugary foods", "Consult oncologist"};
        if (label.contains("eczema"))
            return new String[]{"Hydrating foods", "Spicy foods", "Use moisturizer"};
        if (label.contains("healthy"))
            return new String[]{"Balanced diet", "Junk food", "You're healthy!"};
        return new String[]{"Balanced diet", "Unhealthy food", "Consult dermatologist"};
    }
}
