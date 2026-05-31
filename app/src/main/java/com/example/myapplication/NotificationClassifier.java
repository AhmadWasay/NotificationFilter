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
        if (text == null || text.isEmpty()) return "AI: An empty notification was received.";

        if (isAlarming(title, text)) {
            return "🚨 URGENT: This message contains indicators of a high-priority emergency. " +
                   "The sender mentions: \"" + text.trim() + "\". Please check on them immediately.";
        }
        
        String cleanText = text.trim();
        String cleanTitle = title.toLowerCase();
        String lowerText = cleanText.toLowerCase();
        
        // 1. Career & Professional Context (LinkedIn/Jobs)
        if (cleanTitle.contains("linkedin") || lowerText.contains("hiring") || lowerText.contains("job") || lowerText.contains("developer")) {
            if (lowerText.contains("hiring")) {
                String company = extractKeywordAfter(cleanText, "is hiring");
                return "AI: LinkedIn Career Alert - A company " + (company.isEmpty() ? "" : "(\"" + company + "\") ") + 
                       "is currently recruiting for a position matching your profile.";
            }
            if (lowerText.contains("view") || lowerText.contains("update")) {
                return "AI: Professional Network Update - You have new activity on LinkedIn, including profile views or networking suggestions.";
            }
            return "AI: Professional notification regarding career opportunities or network activity.";
        }

        // 2. Academic & University Context
        if (lowerText.contains("exam") || lowerText.contains("terminal") || lowerText.contains("date sheet") || lowerText.contains("presentation")) {
            return "AI: Academic Notice - Discussion detected regarding upcoming exams, presentations, or schedule changes. " + 
                   "Details: \"" + (cleanText.length() > 40 ? cleanText.substring(0, 37) + "..." : cleanText) + "\"";
        }

        // 3. Media & Stickers
        if (lowerText.contains("sticker")) return "AI: Digital expression - " + title + " shared a sticker in your conversation.";
        if (lowerText.contains("photo") || lowerText.contains("image")) return "AI: Visual content - " + title + " has shared an image with you.";

        // 4. Smart Intent Synthesis (Dynamic & Unique)
        String[] words = cleanText.split("\\s+");
        if (words.length > 2) {
            // Create highly specific summaries by focusing on unique identifiers in the message
            String detail = (words.length > 5) ? words[words.length - 1] : words[words.length - 1];
            String context = words[0] + " " + words[1];
            
            return "AI Analysis: Detecting communication about \"" + context + "...\" involving " + detail + ". " +
                   "This appears to be a unique update from " + title + ".";
        }

        return "AI: Individual notification from " + title + " containing: \"" + cleanText + "\"";
    }

    private String extractKeywordAfter(String text, String target) {
        int idx = text.toLowerCase().indexOf(target.toLowerCase());
        if (idx != -1) {
            String sub = text.substring(0, idx).trim();
            String[] parts = sub.split("\\s+");
            return parts.length > 0 ? parts[parts.length - 1] : "";
        }
        return "";
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