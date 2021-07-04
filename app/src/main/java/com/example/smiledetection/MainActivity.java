package com.example.smiledetection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Facing;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.mlkit.vision.face.FaceContour.LEFT_EYE;
import static com.google.mlkit.vision.face.FaceContour.LEFT_EYEBROW_BOTTOM;
import static com.google.mlkit.vision.face.FaceContour.LEFT_EYEBROW_TOP;
import static com.google.mlkit.vision.face.FaceContour.LOWER_LIP_BOTTOM;
import static com.google.mlkit.vision.face.FaceContour.LOWER_LIP_TOP;
import static com.google.mlkit.vision.face.FaceContour.NOSE_BOTTOM;
import static com.google.mlkit.vision.face.FaceContour.NOSE_BRIDGE;
import static com.google.mlkit.vision.face.FaceContour.RIGHT_EYE;
import static com.google.mlkit.vision.face.FaceContour.RIGHT_EYEBROW_BOTTOM;
import static com.google.mlkit.vision.face.FaceContour.RIGHT_EYEBROW_TOP;
import static com.google.mlkit.vision.face.FaceContour.UPPER_LIP_BOTTOM;
import static com.google.mlkit.vision.face.FaceContour.UPPER_LIP_TOP;

public class MainActivity extends AppCompatActivity implements FrameProcessor {
    private Facing cameraFacing = Facing.FRONT;
    private ImageView imageView;
    private CameraView faceDetectionCameraView;
    private RecyclerView bottom_sheet_recycler;
    private BottomSheetBehavior bottomSheetBehavior;
    private ArrayList<FaceDetectionModel> faceDetectionModels;
    private ImageButton refresh;
    private CheckBox white_bag;
    private FaceDetector detector;
    private FaceDetector real_detector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        faceDetectionModels = new ArrayList<>();
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));

        imageView = findViewById(R.id.face_detection_image_view);
        faceDetectionCameraView = findViewById(R.id.face_detection_camera_view);
        Button toggle = findViewById(R.id.face_detection_camera_toggle_button);
        FrameLayout bottomSheetButton = findViewById(R.id.bottom_sheet_button);
        bottom_sheet_recycler = findViewById(R.id.bottom_sheet_recycler);
        refresh = findViewById(R.id.imageButton);
        white_bag = findViewById(R.id.layer_chip);

        refresh.setOnClickListener(v -> {
            imageView.setImageBitmap(null);
            faceDetectionCameraView.start();
        });

        white_bag.setOnClickListener(v -> {
            if(white_bag.isChecked()){
                imageView.setBackgroundColor(Color.WHITE);
                white_bag.setTextColor(Color.BLACK);
                white_bag.setText("Remove Layer");
            }else{
                imageView.setBackgroundColor(Color.TRANSPARENT);
                refresh.setBackgroundColor(Color.TRANSPARENT);
                white_bag.setText("Add Layer");
//                    white_bag.setTextColor(Color.WHITE);
            }
        });

//        Setup CameraView
        faceDetectionCameraView.setFacing(cameraFacing);
        faceDetectionCameraView.setLifecycleOwner(MainActivity.this);
        faceDetectionCameraView.addFrameProcessor(MainActivity.this);

        bottomSheetButton.setOnClickListener(v -> CropImage.activity().start(MainActivity.this));

        bottom_sheet_recycler.setLayoutManager(new LinearLayoutManager(MainActivity.this));
        bottom_sheet_recycler.setAdapter(new FaceDetectionAdapter(faceDetectionModels,MainActivity.this));

        toggle.setOnClickListener(v -> {
            cameraFacing = (cameraFacing == Facing.FRONT) ? Facing.BACK:Facing.FRONT;
            faceDetectionCameraView.setFacing(cameraFacing);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE){
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK){
                assert result != null;
                Uri imageUri = result.getUri();
                int rotation = result.getRotation();
                try {
                    analyseImage(MediaStore.Images.Media.getBitmap(getContentResolver(),imageUri),rotation);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void analyseImage(Bitmap bitmap, int rotation) {
        if (bitmap == null){
            Toast.makeText(this, "There was an Error Bitmap", Toast.LENGTH_SHORT).show();
            return;
        }
        imageView.setImageBitmap(null);
        faceDetectionModels.clear();
        Objects.requireNonNull(bottom_sheet_recycler.getAdapter()).notifyDataSetChanged();
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        showProgress();

        InputImage image = InputImage.fromBitmap(bitmap,rotation);
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .build();

        detector = FaceDetection.getClient(options);

        Bitmap mutableImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        Task<List<Face>> result =
                detector.process(image)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<Face>>() {
                                    @Override
                                    public void onSuccess(List<Face> faces) {
                                        // Task completed successfully
                                        // ...
                                        detectFaces(faces, mutableImage);
                                        hideProgress();
                                        bottom_sheet_recycler.getAdapter().notifyDataSetChanged();
                                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                                        faceDetectionCameraView.stop();

                                        imageView.setVisibility(View.VISIBLE);
                                        imageView.setImageBitmap(mutableImage);
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        // Task failed with an exception
                                        Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
                                        hideProgress();
                                    }
                                });
    }

    private void detectFaces(List<Face> faces, Bitmap bitmap) {
        if (faces == null || bitmap == null){
            Toast.makeText(this, "Error + detect faces", Toast.LENGTH_SHORT).show();
            return;
        }

        Canvas canvas = new Canvas(bitmap);

        Paint facePaint = new Paint();
        facePaint.setColor(Color.GREEN);
        facePaint.setStyle(Paint.Style.STROKE);
        facePaint.setStrokeWidth(5f);

        Paint faceTextPaint = new Paint();
        faceTextPaint.setColor(Color.WHITE);
        faceTextPaint.setFakeBoldText(true);
        faceTextPaint.setTextSize(30f);
        faceTextPaint.setTypeface(Typeface.SANS_SERIF);

        Paint landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.RED);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(6f);

        for (int i = 0; i <faces.size() ; i++) {
            canvas.drawRect(faces.get(i).getBoundingBox(),facePaint);
            canvas.drawText(
                    "Face " + i,
                    (faces.get(i).getBoundingBox().centerX())- (faces.get(i).getBoundingBox().width() >> 1) +8f,
                    (faces.get(i).getBoundingBox().centerY())- (faces.get(i).getBoundingBox().height() >> 1) -8f,
                    faceTextPaint
            );
            Face face = faces.get(i);

//            leftEye
            if (face.getLandmark(FaceLandmark.LEFT_EYE) !=null){
                FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
                assert leftEye != null;
                canvas.drawCircle(
                        leftEye.getPosition().x,
                        leftEye.getPosition().y,
                        5f,
                        landmarkPaint
                );
            }

//            rightEye
            if (face.getLandmark(FaceLandmark.RIGHT_EYE) !=null){
                FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);
                assert rightEye != null;
                canvas.drawCircle(
                        rightEye.getPosition().x,
                        rightEye.getPosition().y,
                        5f,
                        landmarkPaint
                );
            }

//            nose
            if (face.getLandmark(FaceLandmark.NOSE_BASE) !=null){
                FaceLandmark nose = face.getLandmark(FaceLandmark.NOSE_BASE);
                assert nose != null;
                canvas.drawCircle(
                        nose.getPosition().x,
                        nose.getPosition().y,
                        5f,
                        landmarkPaint
                );
            }

//            leftEar
            if (face.getLandmark(FaceLandmark.LEFT_EAR) !=null){
                FaceLandmark leftEar = face.getLandmark(FaceLandmark.LEFT_EAR);
                assert leftEar != null;
                canvas.drawCircle(
                        leftEar.getPosition().x,
                        leftEar.getPosition().y,
                        5f,
                        landmarkPaint
                );
            }

//          rightEar
            if (face.getLandmark(FaceLandmark.RIGHT_EAR) !=null){
                FaceLandmark rightEar = face.getLandmark(FaceLandmark.RIGHT_EAR);
                assert rightEar != null;
                canvas.drawCircle(
                        rightEar.getPosition().x,
                        rightEar.getPosition().y,
                        5f,
                        landmarkPaint
                );
            }

            if (face.getLandmark(FaceLandmark.MOUTH_LEFT) != null
                    && face.getLandmark(FaceLandmark.MOUTH_BOTTOM) != null
                    && face.getLandmark(FaceLandmark.MOUTH_RIGHT) != null) {
                FaceLandmark leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT);
                FaceLandmark bottomMouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);
                FaceLandmark rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT);
                assert leftMouth != null;
                assert bottomMouth != null;
                canvas.drawLine(
                        Objects.requireNonNull(leftMouth).getPosition().x,
                        leftMouth.getPosition().y,
                        Objects.requireNonNull(bottomMouth).getPosition().x,
                        bottomMouth.getPosition().y,
                        landmarkPaint);
                assert rightMouth != null;
                canvas.drawLine(bottomMouth.getPosition().x,
                        bottomMouth.getPosition().y,
                        Objects.requireNonNull(rightMouth).getPosition().x,
                        rightMouth.getPosition().y, landmarkPaint);
            }

            String def_smiling_emoji = new String(Character.toChars(0x1F604));
            String smiling_emoji = new String(Character.toChars(0x1F642));
            String def =  "Definitely Smiling!! " +def_smiling_emoji + " " + def_smiling_emoji ;
            String smiling = "Smiling!! " + smiling_emoji + " " + smiling_emoji;
            String not = "Not Smiling!!";

//            facedetectionModels
            if (face.getSmilingProbability()>0.8){
                faceDetectionModels.add(new FaceDetectionModel(i,
                        def + "\n"+ "Smiling Probability: " + face.getSmilingProbability()));
            }else if(face.getSmilingProbability()>0.5 && face.getSmilingProbability()<0.8){
                faceDetectionModels.add(new FaceDetectionModel(i,
                        smiling + "\n"+ "Smiling Probability: " + face.getSmilingProbability()));
            }else {
                faceDetectionModels.add(new FaceDetectionModel(i,
                        not + "\n"+ "Smiling Probability: " + face.getSmilingProbability()));
            }
            faceDetectionModels.add(new FaceDetectionModel(i, "Left Eye Open Probability: "+ face.getLeftEyeOpenProbability()));
            faceDetectionModels.add(new FaceDetectionModel(i, "Right Eye Open Probability: "+ face.getRightEyeOpenProbability()));
        }

    }

    private void showProgress() {
        findViewById(R.id.bottom_sheet_button_image).setVisibility(View.GONE);
        findViewById(R.id.bottom_sheet_button_progressbar).setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        findViewById(R.id.bottom_sheet_button_image).setVisibility(View.VISIBLE);
        findViewById(R.id.bottom_sheet_button_progressbar).setVisibility(View.GONE);
    }

    @Override
    public void process(@NonNull Frame frame) {

        int width = frame.getSize().getWidth();
        int height = frame.getSize().getHeight();

//        Log.d("Rotaion", "process: "+ frame.getRotation());
        InputImage image = InputImage.fromByteArray(frame.getData(),width,height,frame.getRotation(),InputImage.IMAGE_FORMAT_NV21);

        // Real-time contour detection
        FaceDetectorOptions realTimeOpts =
                new FaceDetectorOptions.Builder()
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .build();

        real_detector = FaceDetection.getClient(realTimeOpts);
        Task<List<Face>> result = real_detector.process(image)
                .addOnSuccessListener(faces -> {
//                        imageView.setImageBitmap(null);
                    Bitmap bitmap = Bitmap.createBitmap( height ,width, Bitmap.Config.ARGB_8888);

                    Canvas canvas = new Canvas(bitmap);
                    Paint dotPaint = new Paint();
                    dotPaint.setColor(Color.RED);
                    dotPaint.setStyle(Paint.Style.FILL);
                    dotPaint.setStrokeWidth(3f);

                    Paint linePaint = new Paint();
                    linePaint.setColor(Color.GREEN);
                    linePaint.setStyle(Paint.Style.STROKE);
                    linePaint.setStrokeWidth(3f);

                    for (Face face:faces) {
                        Rect bounds = face.getBoundingBox();
                        float diff = bounds.width() /4.0f;
                        canvas.drawRect(bounds, linePaint);

                        int[] list = new int[]{
                                LEFT_EYEBROW_TOP, LEFT_EYEBROW_BOTTOM,
                                RIGHT_EYEBROW_TOP, RIGHT_EYEBROW_BOTTOM,
                                LEFT_EYE, RIGHT_EYE,
                                UPPER_LIP_TOP, UPPER_LIP_BOTTOM,
                                LOWER_LIP_TOP, LOWER_LIP_BOTTOM,
                                NOSE_BRIDGE, NOSE_BOTTOM
                        };

                        for (int contour_int:list) {
                            List<PointF> Contour = face.getContour(contour_int).getPoints();
                            for (int i = 0; i < Contour.size(); i++) {
                                PointF contour = Contour.get(i);
//                                    float contour_X = contour.x;
//                                    float contour_Y = contour.y;
                                if (i != (Contour.size() - 1)) {
                                    canvas.drawLine(contour.x, contour.y, Contour.get(i + 1).x, Contour.get(i + 1).y, linePaint);
                                    if (!white_bag.isChecked()) {
                                        canvas.drawCircle(contour.x, contour.y, 4f, dotPaint);
                                    }
                                }
                            }
                        }
                        List<PointF> faceContours = Objects.requireNonNull(face.getContour(FaceContour.FACE)).getPoints();
                        for (int i = 0; i < faceContours.size(); i++) {
                            PointF faceContour = null;
                            if (i != (faceContours.size() - 1)) {
                                faceContour = faceContours.get(i);
                                canvas.drawLine(
                                        faceContour.x,
                                        faceContour.y,
                                        faceContours.get(i + 1).x,
                                        faceContours.get(i + 1).y,
                                        linePaint
                                );
                            } else {
                                faceContour = faceContours.get(i);
                                canvas.drawLine(
                                        faceContour.x,
                                        faceContour.y,
                                        faceContours.get(0).x,
                                        faceContours.get(0).y,
                                        linePaint);
                            }
                            canvas.drawCircle(
                                    faceContour.x,
                                    faceContour.y,
                                    6f,
                                    dotPaint);
                        }//inner loop
                    }
                    if (cameraFacing == Facing.FRONT) {
                        //Flip image!
                        Matrix matrix = new Matrix();
                        matrix.preScale(-1f, 1f);
                        Bitmap flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                                bitmap.getWidth(), bitmap.getHeight(),
                                matrix, true);
                            imageView.setImageBitmap(flippedBitmap);

                    }else
                        imageView.setImageBitmap(bitmap);
                })
                .addOnFailureListener(e ->
                        Log.d("MainActivity", "onFailure: "  + "Something went wrong!!! Don't Worry " + e.getMessage()));

    }

}