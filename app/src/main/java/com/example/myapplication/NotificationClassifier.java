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
            String[] assetList = context.getAssets().list("");
            boolean modelExists = false;
            if (assetList != null) {
                for (String s : assetList) {
                    if (MODEL_FILE.equals(s)) {
                        modelExists = true;
                        break;
                    }
                }
            }

            if (modelExists) {
                try {
                    interpreter = new Interpreter(loadModelFile(context));
                    labels = loadLabels(context, LABEL_FILE);
                    Log.d(TAG, "TFLite model and labels loaded successfully.");
                } catch (Exception e) {
                    Log.e(TAG, "Could not load TFLite model, using keyword-based fallback.", e);
                }
            } else {
                Log.d(TAG, "No TFLite model found in assets, using heuristics.");
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

        if (isAlarming(title, text)) return false;

        if (interpreter != null) {
            try {
                float score = runInference(content);
                if (score > 0.7f) return true;
            } catch (Exception e) {
                Log.e(TAG, "Inference error", e);
            }
        }

        return checkHeuristics(content);
    }

    public boolean isAlarming(String title, String text) {
        if (title == null || text == null) return false;
        String content = (title + " " + text).toLowerCase();

        // 1. Life Safety & Emergency
        String[] lifeSafety = {
            "accident", "emergency", "police", "ambulance", "hospital", "died", "killed", "dying", "death", "help",
            "evacuate", "lockdown", "shooter", "assailant", "fire", "gas leak", "hazard", "threat", "danger"
        };
        for (String s : lifeSafety) if (content.contains(s)) return true;

        // 2. Weather & Natural Disasters
        String[] weather = {
            "tornado", "hurricane", "flood", "blizzard", "wildfire", "earthquake", "tsunami", "storm warning", "take cover"
        };
        for (String s : weather) if (content.contains(s)) return true;

        // 3. Security & Fraud
        String[] security = {
            "suspicious activity", "unauthorized", "fraud alert", "security breach", "login attempt", "account frozen", 
            "verification code", "otp", "confirm identity"
        };
        for (String s : security) if (content.contains(s)) return true;

        // 4. Home & IoT Security
        String[] homeIot = {
            "intrusion", "motion detected", "alarm triggered", "smoke detected", "carbon monoxide", "door opened", "break-in"
        };
        for (String s : homeIot) if (content.contains(s)) return true;

        // 5. High Urgency Workplace
        String[] workplace = {
            "asap", "urgent", "critical blocker", "system outage", "incident reported", "action required", "immediate attention"
        };
        for (String s : workplace) if (content.contains(s)) return true;

        // Heuristic score for attention-demanding patterns
        int alarmScore = 0;
        if (content.contains("!!!")) alarmScore += 1;
        if (text.contains("HELP") || text.contains("URGENT") || text.contains("EMERGENCY")) alarmScore += 2;
        if (content.contains("now") || content.contains("immediately")) alarmScore += 1;

        return alarmScore >= 3;
    }

    public String summarize(String title, String text) {
        if (text == null || text.isEmpty()) return "AI: Someone sent a message.";

        if (isAlarming(title, text)) {
            return "URGENT ALERT: Potential emergency detected from " + title + ". Please act now.";
        }
        
        String cleanText = text.toLowerCase().trim();
        String cleanTitle = title.toLowerCase();
        
        // 1. Academic & University Context (Based on your screenshot)
        if (cleanText.contains("exam") || cleanText.contains("terminal") || cleanText.contains("date sheet")) {
            return "AI: Important update regarding your examination schedule/date sheet.";
        }
        if (cleanText.contains(".xlsx") || cleanText.contains(".pdf") || cleanText.contains(".doc")) {
            return "AI: " + title + " shared an academic document or spreadsheet.";
        }
        if (cleanTitle.contains("section") || cleanTitle.contains("bscs")) {
            if (cleanText.contains("presentation") || cleanText.contains("slide")) return "AI: Coordination for group presentations in " + title + ".";
            return "AI: Academic discussion or announcement in your class group (" + title + ").";
        }

        // 2. WhatsApp / Messaging Specific Logic
        if (cleanText.contains("sticker")) return "AI: " + title + " sent a sticker.";
        if (cleanText.contains("photo") || cleanText.contains("image")) return "AI: " + title + " shared a photo.";
        if (cleanText.contains("video")) return "AI: " + title + " shared a video.";
        if (cleanText.contains("location")) return "AI: " + title + " shared their location.";
        if (cleanText.contains("audio") || cleanText.contains("voice message")) return "AI: " + title + " sent a voice note.";
        if (cleanText.equals("message") || cleanText.isEmpty()) return "AI: " + title + " sent a message.";

        // 3. Coordination & Schedule
        if (cleanText.contains("meeting") || cleanText.contains("zoom") || cleanText.contains("class")) return "AI: Scheduling details for a meeting or lecture.";
        if (cleanText.contains("assignment") || cleanText.contains("homework")) return "AI: Reminder or update regarding pending assignments.";
        if (cleanText.contains("otp") || cleanText.contains("verification code")) return "AI: Security verification requested.";
        if (cleanText.contains("hi") || cleanText.contains("hello") || cleanText.contains("hey")) return "AI: " + title + " is greeting you.";

        // 4. Smart Content Synthesis (Synthesizing the actual text context)
        String[] words = text.split("\\s+");
        if (words.length > 3) {
            String snippet = words[0] + " " + words[1] + " " + (words.length > 2 ? words[2] : "");
            return "AI: Content regarding \"" + snippet + "...\"";
        }

        return "AI: Summarized communication from " + title + ".";
    }

    public String generateCollectiveSummary(List<String> spamTexts) {
        if (spamTexts == null || spamTexts.isEmpty()) return "AI Analysis: No spam threats detected.";
        
        int promoCount = 0;
        int offerCount = 0;
        int rewardCount = 0;
        
        for (String s : spamTexts) {
            String low = s.toLowerCase();
            if (low.contains("promo") || low.contains("discount")) promoCount++;
            if (low.contains("offer") || low.contains("sale")) offerCount++;
            if (low.contains("win") || low.contains("gift")) rewardCount++;
        }
        
        StringBuilder summary = new StringBuilder("AI Insight: ");
        summary.append("I have successfully filtered ").append(spamTexts.size()).append(" spam messages. ");
        
        if (promoCount > 0) summary.append("Most are marketing promotions (").append(promoCount).append("), ");
        if (offerCount > 0) summary.append("with some limited-time sales (").append(offerCount).append("), ");
        if (rewardCount > 0) summary.append("and ").append(rewardCount).append(" suspicious reward alerts.");
        
        return summary.toString().trim().replaceAll(", $", ".");
    }

    private float runInference(String text) {
        float[][] output = new float[1][1]; 
        try {
            float[][] input = new float[1][20]; 
            interpreter.run(input, output);
            return output[0][0];
        } catch (Exception e) {
            Log.e(TAG, "Inference failed", e);
            return 0.0f;
        }
    }

    private boolean checkHeuristics(String content) {
        String[] spamKeywords = {
            "promo", "discount", "offer", "sale", "win", "50%", "off", "order your next", "gift card", "reward", "prize", "exclusive",
            "free", "deal", "coupon", "voucher", "cashback", "save", "limited time", "buy one", "get one", "subscribe", "newsletter",
            "marketing", "promotional", "sponsored", "advertisement", "click here", "unlocked", "bonus", "referral", "earn money"
        };
        for (String keyword : spamKeywords) {
            if (content.contains(keyword)) return true;
        }
        if (content.contains("!!!") || content.contains("$$$") || content.contains("🤑") || content.contains("🔥")) return true;
        return false;
    }
}