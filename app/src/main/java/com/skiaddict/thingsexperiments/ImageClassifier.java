package com.skiaddict.thingsexperiments;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.util.Log;

import junit.framework.Assert;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Created by jewatts on 8/22/17.
 */

public class ImageClassifier {

    private static final String TAG = ImageClassifier.class.getSimpleName();

    private static final String MODEL_FILE = "file:///android_asset/tensorflow_inception_graph.pb";
    private static final String LABELS_FILE = "imagenet_comp_graph_label_strings.txt";
    public static final int IMAGE_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    public static final int NUM_CLASSES = 1008;

    public static final String INPUT_NAME = "input:0";
    public static final String OUTPUT_OPERATION = "output";
    public static final String OUTPUT_NAME = OUTPUT_OPERATION + ":0";
    public static final String[] OUTPUT_NAMES = {OUTPUT_NAME};
    public static final long[] NETWORK_STRUCTURE = {1, IMAGE_SIZE, IMAGE_SIZE, 3};
    private static final int MAX_BEST_RESULTS = 3;
    private static final float RESULT_CONFIDENCE_THRESHOLD = 0.1f;

    private String[] labels;
    private float[] floatValues;
    private int[] intValues;
    private float[] outputs;
    private TensorFlowInferenceInterface tensorFlowInferenceInterface;

    public ImageClassifier(Context context) {
        tensorFlowInferenceInterface = new TensorFlowInferenceInterface(context.getAssets(), MODEL_FILE);

        labels = readLabels(context);

        intValues = new int[IMAGE_SIZE * IMAGE_SIZE];
        floatValues = new float[IMAGE_SIZE * IMAGE_SIZE * 3];
        outputs = new float[NUM_CLASSES];
    }

    public List<ClassificationResult> doRecognize(Bitmap image) {

        // read pixels from image.
        float[] pixels = getPixels(image, intValues, floatValues);

        // Feed the pixels of the image into the TensorFlow Neural Network
        tensorFlowInferenceInterface.feed(INPUT_NAME, pixels, NETWORK_STRUCTURE);

        // Run the TensorFlow Neural Network with the provided input
        tensorFlowInferenceInterface.run(OUTPUT_NAMES);

        // Extract the output from the neural network back into an array of confidence per category
        tensorFlowInferenceInterface.fetch(OUTPUT_NAME, outputs);

        // Get the results with the highest confidence and map them to their labels
        return orderResults(outputs, labels);
    }

    public Bitmap cropAndRescaleBitmap(final Bitmap src) {
        Bitmap dst = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888);

        Assert.assertEquals(dst.getWidth(), dst.getHeight());
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.preTranslate(translateX, translateY);

        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);

        return dst;
    }

    private static String[] readLabels(Context context) {
        AssetManager assetManager = context.getAssets();
        ArrayList<String> result = new ArrayList<>();
        try (InputStream is = assetManager.open(LABELS_FILE);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                result.add(line);
            }
            return result.toArray(new String[result.size()]);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read labels from " + LABELS_FILE);
        }
    }

    private static float[] getPixels(Bitmap bitmap, int[] intValues, float[] floatValues) {
        if (bitmap.getWidth() != IMAGE_SIZE || bitmap.getHeight() != IMAGE_SIZE) {
            // rescale the bitmap if needed
            bitmap = ThumbnailUtils.extractThumbnail(bitmap, IMAGE_SIZE, IMAGE_SIZE);
        }

        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        for (int i = 0; i < intValues.length; ++i) {
            final int val = intValues[i];
            floatValues[i * 3] = (((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD;
            floatValues[i * 3 + 1] = (((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD;
            floatValues[i * 3 + 2] = ((val & 0xFF) - IMAGE_MEAN) / IMAGE_STD;
        }
        return floatValues;
    }


    private static List<ClassificationResult> orderResults (float[] confidenceLevels, String[] labels) {
        PriorityQueue<ClassificationResult> pq = new PriorityQueue<>(MAX_BEST_RESULTS,
                new Comparator<ClassificationResult>() {
                    @Override
                    public int compare(ClassificationResult lhs, ClassificationResult rhs) {
                        // Intentionally reversed to put high confidence at the head of the queue.
                        return Float.compare(rhs.confidence, lhs.confidence);
                    }
                });

        for (int ix = 0; ix < confidenceLevels.length; ++ix) {
            if (confidenceLevels[ix] > RESULT_CONFIDENCE_THRESHOLD) {
                pq.add(new ClassificationResult(labels[ix], confidenceLevels[ix]));
            }
        }

        ArrayList<ClassificationResult> recognitions = new ArrayList<ClassificationResult>();
        int recognitionsSize = Math.min(pq.size(), MAX_BEST_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }
        return recognitions;
    }


    public static class ClassificationResult {
        ClassificationResult (String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }

        public String label;
        public float confidence;
    }
}
