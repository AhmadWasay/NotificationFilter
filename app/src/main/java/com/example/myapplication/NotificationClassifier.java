package com.example.myapplication;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class NotificationClassifier {
    private static final String TAG = "NotificationClassifier";
    private static final String MODEL_FILE = "model.tflite";
    private static final String LABEL_FILE = "labels.txt";

    private Interpreter interpreter;
    private List<String> labels;

    public NotificationClassifier(Context context) {
        try {
            // In a real scenario, we would load a real .tflite model here.
            // Since we can't provide a binary model file, we'll implement the loading structure
            // and use a robust fallback logic if the file is missing or invalid.
            if (context.getAssets().list("").length > 0) {
                try {
                    interpreter = new Interpreter(loadModelFile(context));
                    labels = loadLabels(context, LABEL_FILE);
                    Log.d(TAG, "TFLite model and labels loaded successfully.");
                } catch (Exception e) {
                    Log.e(TAG, "Could not load TFLite model, using keyword-based fallback.", e);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error checking assets", e);
        }
    }

    private List<String> loadLabels(Context context, String fileName) throws IOException {
        List<String> labels = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(fileName)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line);
            }
        }
        return labels;
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public boolean isSpam(String title, String text) {
        if (title == null || text == null) return false;
        String content = (title + " " + text).toLowerCase();

        // Check if alarming first - alarming messages should never be considered spam
        if (isAlarming(title, text)) return false;

        // 1. Run TFLite Inference if model is loaded
        if (interpreter != null) {
            float score = runInference(content);
            Log.d(TAG, "ML Confidence Score: " + score);
            if (score > 0.7f) return true; // Threshold for spam
        }

        // 2. Keyword/Heuristic Fallback (Baseline)
        return checkHeuristics(content);
    }

    public boolean isAlarming(String title, String text) {
        if (title == null || text == null) return false;
        String content = (title + " " + text).toLowerCase();

        // 1. Critical "Red Line" Keywords (Immediate Alarm)
        String[] critical = {"accident", "emergency", "police", "ambulance", "hospital", "died", "killed"};
        for (String c : critical) {
            if (content.contains(c)) return true;
        }

        // 2. Urgency Scoring (AI Heuristic)
        int alarmScore = 0;
        String[] urgentTokens = {"urgent", "help", "danger", "blood", "fire", "stolen", "lost", "missing", "immediate"};
        for (String token : urgentTokens) {
            if (content.contains(token)) alarmScore += 2;
        }
        
        // Intensity detection (Multiple exclamation marks increase alarm score)
        if (content.contains("!!!")) alarmScore += 1;
        
        // Check original text for SHOUTING (HELP)
        if (text.contains("HELP")) alarmScore += 2;

        return alarmScore >= 3; // Trigger alarm if score is high enough
    }

    public String summarize(String text) {
        if (text == null || text.isEmpty()) return "";
        
        String trimmed = text.trim();
        if (trimmed.length() < 50) return trimmed;

        String[] sentences = trimmed.split("[.!?]");
        if (sentences.length > 0) {
            String primary = sentences[0];
            if (primary.length() > 65) {
                return primary.substring(0, 62) + "...";
            }
            return primary;
        }
        
        return trimmed.substring(0, 60) + "...";
    }

    public String generateCollectiveSummary(List<String> spamTexts) {
        if (spamTexts == null || spamTexts.isEmpty()) return "No spam detected.";
        
        // AI Logic: Identify common themes in the spam batch
        int promoCount = 0;
        int offerCount = 0;
        int rewardCount = 0;
        
        for (String s : spamTexts) {
            String low = s.toLowerCase();
            if (low.contains("promo") || low.contains("discount")) promoCount++;
            if (low.contains("offer") || low.contains("sale")) offerCount++;
            if (low.contains("win") || low.contains("gift") || low.contains("reward")) rewardCount++;
        }
        
        StringBuilder summary = new StringBuilder("Batch Summary: ");
        summary.append("Detected ").append(spamTexts.size()).append(" spam notifications. ");
        
        if (promoCount > 0) summary.append(promoCount).append(" promotional messages, ");
        if (offerCount > 0) summary.append(offerCount).append(" limited-time offers, ");
        if (rewardCount > 0) summary.append(rewardCount).append(" potential reward scams. ");
        
        return summary.toString().replaceAll(", $", ".");
    }

    private float runInference(String text) {
        // Simple tokenization placeholder (Models usually expect a fixed-length int array)
        // In a real TFLite implementation, you'd map words to IDs from a vocabulary file.
        float[][] output = new float[1][1]; 
        try {
            // This is a placeholder for the actual input tensor preparation
            // Most text models use a 1D array of floats or ints
            float[][] input = new float[1][20]; // Assuming sequence length of 20
            interpreter.run(input, output);
            return output[0][0];
        } catch (Exception e) {
            Log.e(TAG, "Inference failed", e);
            return 0.0f;
        }
    }

    private boolean checkHeuristics(String content) {
        String[] spamKeywords = {"promo", "discount", "offer", "sale", "win", "50%", "off", "order your next", "gift card", "reward", "prize", "exclusive"};
        for (String keyword : spamKeywords) {
            if (content.contains(keyword)) return true;
        }
        
        // Detect "shouting" or excessive punctuation
        if (content.contains("!!!") || content.contains("$$$")) return true;

        return false;
    }
}